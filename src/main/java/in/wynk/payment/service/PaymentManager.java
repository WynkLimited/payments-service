package in.wynk.payment.service;

import in.wynk.commons.constants.BaseConstants;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.BeanLocatorFactory;
import in.wynk.coupon.core.constant.CouponProvisionState;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dto.CouponDTO;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;

import static in.wynk.commons.constants.BaseConstants.CLIENT_ID;
import static in.wynk.commons.constants.BaseConstants.SERVICE;

@Slf4j
@Service
public class PaymentManager {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ISqsManagerService sqsManagerService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;

    public PaymentManager(ICouponManager couponManager, PaymentCachingService cachingService, ISqsManagerService sqsManagerService, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager) {
        this.couponManager = couponManager;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
    }

    public BaseResponse<?> doCharging(String uid, String msisdn, ChargingRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = initiateTransaction(request.getPlanId(), request.isAutoRenew(), uid, msisdn, request.getItemId(), request.getCouponId(), paymentCode);
        final IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        final BaseResponse<?> baseResponse = chargingService.doCharging(request);
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder()
                .paymentCode(transaction.getPaymentChannel())
                .transactionEvent(transaction.getType())
                .transactionId(transaction.getIdStr())
                .itemId(transaction.getItemId())
                .planId(transaction.getPlanId())
                .msisdn(transaction.getMsisdn())
                .uid(transaction.getUid())
                .build());
        return baseResponse;
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
                            .clientId(transaction.getClientId())
                            .transactionId(transaction.getIdStr())
                            .paymentCode(transaction.getPaymentChannel())
                            .transactionEvent(transaction.getType())
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
        final PaymentCode paymentCode = request.paymentCode();
        final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
        final boolean autoRenew = selectedPlan.getPlanType() == PlanType.SUBSCRIPTION;
        final Transaction transaction = initiateTransaction(request.getPlanId(), autoRenew, request.getUid(), request.getMsisdn(), null, null, paymentCode);
        final TransactionStatus initialStatus = transaction.getStatus();
        SessionContextHolder.<SessionDTO>getBody().put(PaymentConstants.TXN_ID, transaction.getId());
        final IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantIapPaymentVerificationService.class);
        final BaseResponse<?> response = verificationService.verifyReceipt(request);
        final TransactionStatus finalStatus = transaction.getStatus();
        transactionManager.updateAndSyncPublish(transaction, initialStatus, finalStatus);
        if(finalStatus == TransactionStatus.SUCCESS){
            exhaustCouponIfApplicable();
        }
        return response;
    }

    public void doRenewal(PaymentRenewalChargingMessage message) {
        IMerchantPaymentRenewalService merchantPaymentRenewalService = BeanLocatorFactory.getBean(message.getPaymentCode().getCode(), IMerchantPaymentRenewalService.class);
        merchantPaymentRenewalService.doRenewal(message.getPaymentRenewalRequest());
    }

    private Transaction initiateTransaction(int planId, boolean autoRenew, String uid, String msisdn, String itemId, String couponId, PaymentCode paymentCode) {
        final CouponDTO coupon;
        final double amountToBePaid;
        final double finalAmountToBePaid;
        final SessionDTO session = SessionContextHolder.getBody();
        final String service = session.get(SERVICE);
        final String clientId = session.get(CLIENT_ID);
        final TransactionInitRequest.TransactionInitRequestBuilder builder = TransactionInitRequest.builder().uid(uid).msisdn(msisdn).paymentCode(paymentCode).clientId(clientId);

        if (StringUtils.isNotEmpty(itemId)) {
            builder.itemId(itemId);
            builder.event(TransactionEvent.POINT_PURCHASE);
            amountToBePaid = session.get(BaseConstants.POINT_PURCHASE_ITEM_PRICE);
            coupon = getCoupon(couponId, msisdn, uid, service, itemId, paymentCode, null);
        } else {
            builder.planId(planId);
            PlanDTO selectedPlan = cachingService.getPlan(planId);
            amountToBePaid = selectedPlan.getFinalPrice();
            builder.event(autoRenew ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE);
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
        if(StringUtils.isNotEmpty(transaction.getCoupon())) {
            try {
                couponManager.exhaustCoupon(transaction.getUid(), transaction.getCoupon());
            } catch (WynkRuntimeException e) {
                log.error(e.getMarker(), e.getMessage(), e);
            }
        }
    }

}
