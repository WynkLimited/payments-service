package in.wynk.payment.service;

import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.constant.CouponProvisionState;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponDTO;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.common.messages.PaymentRecurringSchedulingMessage;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.TXN_ID;

@Slf4j
@Service
public class PaymentManager {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ISqsManagerService sqsManagerService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;

    public PaymentManager(ICouponManager couponManager, PaymentCachingService cachingService, ISqsManagerService sqsManagerService, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager, IMerchantTransactionService merchantTransactionService, IRecurringPaymentManagerService recurringPaymentManagerService) {
        this.couponManager = couponManager;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
        this.merchantTransactionService = merchantTransactionService;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
    }

    public BaseResponse<?> doCharging(String uid, String msisdn, ChargingRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = initiateTransaction(request.isAutoRenew(), request.getPlanId(), uid, msisdn, request.getItemId(), request.getCouponId(), paymentCode);
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder()
                .paymentCode(transaction.getPaymentChannel())
                .paymentEvent(transaction.getType())
                .transactionId(transaction.getIdStr())
                .itemId(transaction.getItemId())
                .planId(transaction.getPlanId())
                .msisdn(transaction.getMsisdn())
                .uid(transaction.getUid())
                .build());
        final IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        return chargingService.doCharging(request);
    }

    @TransactionAware(txnId = "#request.transactionId")
    public BaseResponse<?> handleCallback(CallbackRequest request, PaymentCode paymentCode) {
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        final BaseResponse<?> baseResponse;
        try {
            baseResponse = callbackService.handleCallback(request);
        } finally {
            TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.updateAndSyncPublish(transaction, existingStatus, finalStatus);
            if (existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
                exhaustCouponIfApplicable();
            }
        }
        return baseResponse;
    }

    @TransactionAware(txnId = "#request.transactionId")
    public BaseResponse<?> status(ChargingStatusRequest request, PaymentCode paymentCode, boolean isSync) {
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        final BaseResponse<?> baseResponse;
        try {
            baseResponse = statusService.status(request);
        } finally {
            TransactionStatus finalStatus = transaction.getStatus();
            if (!isSync) {
                transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
                if (existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
                    exhaustCouponIfApplicable();
                }
                if (existingStatus != finalStatus) {
                    eventPublisher.publishEvent(PaymentReconciledEvent.builder()
                            .uid(transaction.getUid())
                            .msisdn(transaction.getMsisdn())
                            .itemId(transaction.getItemId())
                            .planId(transaction.getPlanId())
                            .clientAlias(transaction.getClientAlias())
                            .transactionId(transaction.getIdStr())
                            .paymentCode(transaction.getPaymentChannel())
                            .paymentEvent(transaction.getType())
                            .transactionStatus(transaction.getStatus())
                            .build());
                }
            }
        }
        return baseResponse;
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final IMerchantVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        return verificationService.doVerify(request);
    }

    public BaseResponse<?> doVerifyIap(IapVerificationRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
        final boolean autoRenew = selectedPlan.getPlanType() == PlanType.SUBSCRIPTION;
        final Transaction transaction;
        if (StringUtils.isEmpty(request.getOs())) {
            transaction = initiateTransactionForPlan(autoRenew, request.getPlanId(), request.getUid(), request.getMsisdn(), null, paymentCode);
            SessionContextHolder.<SessionDTO>getBody().put(PaymentConstants.TXN_ID, transaction.getId());
        } else {
            String clientAlias = getClientAlias(SecurityContextHolder.getContext().getAuthentication().getName());
            transaction = initiateTransaction(request.getPlanId(), request.getUid(), request.getMsisdn(), clientAlias,  paymentCode, autoRenew ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE);
        }
        final TransactionStatus initialStatus = transaction.getStatus();
        final IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantIapPaymentVerificationService.class);
        final BaseResponse<?> response = verificationService.verifyReceipt(request);
        final TransactionStatus finalStatus = transaction.getStatus();
        if (StringUtils.isEmpty(request.getOs())) {
            transactionManager.updateAndSyncPublish(transaction, initialStatus, finalStatus);
        } else {
            transactionManager.updateAndAsyncPublish(transaction, initialStatus, finalStatus);
        }
        if (finalStatus == TransactionStatus.SUCCESS) {
            exhaustCouponIfApplicable();
        }
        return response;
    }

    @ClientAware(clientId = "#clientId")
    private String getClientAlias(String clientId) {
        ClientDetails clientDetails = (ClientDetails) ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
        return clientDetails.getAlias();
    }

    public void doRenewal(PaymentRenewalChargingRequest request, PaymentCode paymentCode) {
        final Transaction transaction = initiateTransaction(request.getPlanId(), request.getUid(), request.getMsisdn(), request.getClientAlias(), paymentCode, PaymentEvent.RENEW);
        Map<String, Object> paymentMetaData = transaction.getPaymentMetaData();
        paymentMetaData.put(PaymentConstants.RENEWAL, true);
        transaction.setPaymentMetaData(paymentMetaData);
        final TransactionStatus initialStatus = transaction.getStatus();
        IMerchantPaymentRenewalService merchantPaymentRenewalService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentRenewalService.class);
        try {
            merchantPaymentRenewalService.doRenewal(request);
        } finally {
            if(merchantPaymentRenewalService.supportsRenewalReconciliation()){
                sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder()
                        .paymentCode(transaction.getPaymentChannel())
                        .paymentEvent(transaction.getType())
                        .transactionId(transaction.getIdStr())
                        .itemId(transaction.getItemId())
                        .planId(transaction.getPlanId())
                        .msisdn(transaction.getMsisdn())
                        .uid(transaction.getUid())
                        .build());
            }
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.updateAndAsyncPublish(transaction, initialStatus, finalStatus);
        }
    }

    private Transaction initiateTransaction(int planId, String uid, String msisdn, String clientAlias, PaymentCode paymentCode, PaymentEvent paymentEvent) {
        TransactionContext.set(transactionManager.initiateTransaction(TransactionInitRequest.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .event(paymentEvent)
                .paymentCode(paymentCode)
                .clientAlias(clientAlias)
                .amount(cachingService.getPlan(planId).getFinalPrice())
                .build()));
        return TransactionContext.get();
    }

    private Transaction initiateTransactionForItem(boolean autoRenew, String uid, String msisdn, String itemId, String couponId, PaymentCode paymentCode) {
        return initiateTransaction(false, 0, uid, msisdn, itemId, couponId, paymentCode);
    }

    private Transaction initiateTransactionForPlan(boolean autoRenew, int planId, String uid, String msisdn, String couponId, PaymentCode paymentCode) {
        return initiateTransaction(autoRenew, planId, uid, msisdn, null, couponId, paymentCode);
    }

    private Transaction initiateTransaction(boolean autoRenew, int planId, String uid, String msisdn, String itemId, String couponId, PaymentCode paymentCode) {
        final CouponDTO coupon;
        final double amountToBePaid;
        final double finalAmountToBePaid;
        final SessionDTO session = SessionContextHolder.getBody();
        final String service = session.get(SERVICE);
        final String clientAlias = session.get(CLIENT);
        final TransactionInitRequest.TransactionInitRequestBuilder builder = TransactionInitRequest.builder().uid(uid).msisdn(msisdn).paymentCode(paymentCode).clientAlias(clientAlias);

        if (StringUtils.isNotEmpty(itemId)) {
            builder.itemId(itemId);
            builder.event(PaymentEvent.POINT_PURCHASE);
            amountToBePaid = session.get(BaseConstants.POINT_PURCHASE_ITEM_PRICE);
            coupon = getCoupon(couponId, msisdn, uid, service, itemId, paymentCode, null);
        } else {
            builder.planId(planId);
            PlanDTO selectedPlan = cachingService.getPlan(planId);
            amountToBePaid = selectedPlan.getFinalPrice();
            builder.event(autoRenew ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE);
            coupon = getCoupon(couponId, msisdn, uid, service, null, paymentCode, selectedPlan);
        }

        if (coupon != null) {
            builder.couponId(couponId).discount(coupon.getDiscountPercent());
            finalAmountToBePaid = getFinalAmount(amountToBePaid, coupon);
        } else {
            finalAmountToBePaid = amountToBePaid;
        }
        builder.amount(finalAmountToBePaid).build();
        TransactionContext.set(transactionManager.initiateTransaction(builder.build()));
        return TransactionContext.get();
    }

    private CouponDTO getCoupon(String couponId, String msisdn, String uid, String service, String itemId, PaymentCode paymentCode, PlanDTO selectedPlan) {
        if (!StringUtils.isEmpty(couponId)) {
            CouponProvisionRequest couponProvisionRequest = CouponProvisionRequest.builder()
                    .couponCode(couponId).msisdn(msisdn).service(service).paymentCode(paymentCode.getCode()).selectedPlan(selectedPlan).itemId(itemId).uid(uid).source(ProvisionSource.MANAGED).build();
            CouponResponse couponResponse = couponManager.evalCouponEligibility(couponProvisionRequest);
            return couponResponse.getState() != CouponProvisionState.INELIGIBLE ? couponResponse.getCoupon() : null;
        } else {
            return null;
        }
    }

    private double getFinalAmount(double itemPrice, CouponDTO coupon) {
        double discount = coupon.getDiscountPercent();
        DecimalFormat df = new DecimalFormat("#.00");
        return Double.parseDouble(df.format(itemPrice - (itemPrice * discount) / 100));
    }

    private void exhaustCouponIfApplicable() {
        Transaction transaction = TransactionContext.get();
        if (StringUtils.isNotEmpty(transaction.getCoupon())) {
            try {
                couponManager.exhaustCoupon(transaction.getUid(), transaction.getCoupon());
            } catch (WynkRuntimeException e) {
                log.error(e.getMarker(), e.getMessage(), e);
            }
        }
    }

    public void addToPaymentRenewalMigration(PaymentRecurringSchedulingMessage message) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(message.getNextChargingDate());
        int planId = message.getPlanId();
        PaymentCode paymentCode = PaymentCode.getFromCode(message.getPaymentCode());
        double amount = cachingService.getPlan(planId).getFinalPrice();
        Transaction transaction = transactionManager.initiateTransaction(TransactionInitRequest.builder()
                .uid(message.getUid())
                .msisdn(message.getMsisdn())
                .clientAlias(message.getClientAlias())
                .planId(planId)
                .amount(amount)
                .paymentCode(paymentCode)
                .event(message.getEvent())
                .status(TransactionStatus.MIGRATED.getValue())
                .build());
        transaction.putValueInPaymentMetaData(MIGRATED_NEXT_CHARGING_DATE, calendar);
        IMerchantTransactionDetailsService merchantTransactionDetailsService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantTransactionDetailsService.class);
        message.getPaymentMetaData().put(MIGRATED, Boolean.TRUE.toString());
        message.getPaymentMetaData().put(TXN_ID, transaction.getIdStr());
        MerchantTransaction merchantTransaction = merchantTransactionDetailsService.getMerchantTransactionDetails(message.getPaymentMetaData());
        merchantTransactionService.upsert(merchantTransaction);
        transactionManager.updateAndAsyncPublish(transaction, TransactionStatus.INPROGRESS, transaction.getStatus());
    }

}
