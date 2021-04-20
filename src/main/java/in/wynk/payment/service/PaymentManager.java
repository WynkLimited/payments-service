package in.wynk.payment.service;

import in.wynk.client.aspect.advice.ClientAware;
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
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.StatusMode;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.ClientCallbackEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.ClientCallbackPayloadWrapper;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service
public class PaymentManager {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ISqsManagerService sqsManagerService;
    private final ApplicationEventPublisher eventPublisher;
    private final IClientCallbackService clientCallbackService;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;

    public PaymentManager(ICouponManager couponManager, PaymentCachingService cachingService, ISqsManagerService sqsManagerService, ApplicationEventPublisher eventPublisher, IClientCallbackService clientCallbackService, ITransactionManagerService transactionManager, IMerchantTransactionService merchantTransactionService) {
        this.couponManager = couponManager;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
        this.clientCallbackService = clientCallbackService;
        this.merchantTransactionService = merchantTransactionService;
    }

    @TransactionAware(txnId = "#request.originalTransactionId")
    public BaseResponse<?> initRefund(PaymentRefundInitRequest request) {
        Transaction originalTransaction = TransactionContext.get();
        final Transaction refundTransaction = initiateRefundTransaction(originalTransaction.getUid(), originalTransaction.getMsisdn(), originalTransaction.getPlanId(), originalTransaction.getItemId(), originalTransaction.getClientAlias(), originalTransaction.getAmount(), originalTransaction.getPaymentChannel());
        String externalReferenceId = merchantTransactionService.getPartnerReferenceId(request.getOriginalTransactionId());
        originalTransaction.putValueInPaymentMetaData(EXTERNAL_TRANSACTION_ID, externalReferenceId);
        final IMerchantPaymentRefundService refundService = BeanLocatorFactory.getBean(originalTransaction.getPaymentChannel().getCode(), IMerchantPaymentRefundService.class);
        AbstractPaymentRefundRequest refundRequest = AbstractPaymentRefundRequest.from(originalTransaction, refundTransaction, request.getReason());
        BaseResponse<?> refundInitResponse = refundService.refund(refundRequest);
        AbstractPaymentRefundResponse refundResponse = (AbstractPaymentRefundResponse) refundInitResponse.getBody();
        if (refundResponse.getTransactionStatus() != TransactionStatus.FAILURE) {
            sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder()
                    .paymentCode(refundTransaction.getPaymentChannel())
                    .extTxnId(refundResponse.getExternalReferenceId())
                    .transactionId(refundTransaction.getIdStr())
                    .paymentEvent(refundTransaction.getType())
                    .itemId(refundTransaction.getItemId())
                    .planId(refundTransaction.getPlanId())
                    .msisdn(refundTransaction.getMsisdn())
                    .uid(refundTransaction.getUid())
                    .build());
        }
        return refundInitResponse;
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
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.updateAndSyncPublish(transaction, existingStatus, finalStatus);
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
        }
        return baseResponse;
    }

    @ClientAware(clientAlias = "#clientAlias")
    public BaseResponse<?> handleNotification(String clientAlias, CallbackRequest callbackRequest, PaymentCode paymentCode) {
        final IReceiptDetailService receiptDetailService = BeanLocatorFactory.getBean(paymentCode.getCode(), IReceiptDetailService.class);
        Optional<ReceiptDetails> optionalReceiptDetails = receiptDetailService.getReceiptDetails(callbackRequest);
        if (optionalReceiptDetails.isPresent()) {
            ReceiptDetails receiptDetails = optionalReceiptDetails.get();
            String txnId = initiateTransaction(receiptDetails.getPlanId(), receiptDetails.getUid(), receiptDetails.getMsisdn(), paymentCode);
            return handleCallback(CallbackRequest.builder().body(callbackRequest.getBody()).transactionId(txnId).build(), paymentCode);
        }
        return BaseResponse.status(false);
    }

    @TransactionAware(txnId = "#request.transactionId")
    public BaseResponse<?> status(AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(request.getPaymentCode(), IMerchantPaymentStatusService.class);
        final BaseResponse<?> baseResponse;
        request.setPlanId(transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(transaction.getPlanId()).getLinkedFreePlanId() : transaction.getPlanId());
        try {
            baseResponse = statusService.status(request);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw e;
        } finally {
            if (request.getMode() == StatusMode.SOURCE) {
                TransactionStatus finalStatus = transaction.getStatus();
                transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
                if (existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
                    exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
                }
                publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
            }
        }
        return baseResponse;
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final IMerchantVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        return verificationService.doVerify(request);
    }

    @ClientAware(clientId = "#clientId")
    public BaseResponse<?> doVerifyIap(String clientId, IapVerificationRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantIapPaymentVerificationService.class);
        LatestReceiptResponse latestReceiptResponse = verificationService.getLatestReceiptResponse(request);
        final Transaction transaction = initiateTransactionForPlan(latestReceiptResponse.isFreeTrial(), request.getPlanId(), request.getUid(), request.getMsisdn(), paymentCode);
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder()
                .extTxnId(latestReceiptResponse.getExtTxnId())
                .paymentCode(transaction.getPaymentChannel())
                .transactionId(transaction.getIdStr())
                .paymentEvent(transaction.getType())
                .itemId(transaction.getItemId())
                .planId(transaction.getPlanId())
                .msisdn(transaction.getMsisdn())
                .uid(transaction.getUid())
                .build());
        final TransactionStatus initialStatus = transaction.getStatus();
        SessionContextHolder.<SessionDTO>getBody().put(PaymentConstants.TXN_ID, transaction.getId());
        try {
            return verificationService.verifyReceipt(latestReceiptResponse);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.updateAndSyncPublish(transaction, initialStatus, finalStatus);
            exhaustCouponIfApplicable(initialStatus, finalStatus, transaction);
        }
    }

    public void doRenewal(PaymentRenewalChargingRequest request, PaymentCode paymentCode) {
        final Transaction transaction = initiateTransactionForRenew(request.getPlanId(), request.getUid(), request.getMsisdn(), request.getClientAlias(), paymentCode);
        transaction.putValueInPaymentMetaData(RENEWAL, true);
        transaction.putValueInPaymentMetaData(ATTEMPT_SEQUENCE, request.getAttemptSequence() + 1);
        final TransactionStatus initialStatus = transaction.getStatus();
        IMerchantPaymentRenewalService merchantPaymentRenewalService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentRenewalService.class);
        try {
            merchantPaymentRenewalService.doRenewal(request);
        } finally {
            if (merchantPaymentRenewalService.supportsRenewalReconciliation()) {
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

    public void sendClientCallback(String clientAlias, ClientCallbackRequest request) {
        clientCallbackService.sendCallback(ClientCallbackPayloadWrapper.<ClientCallbackRequest>builder().clientAlias(clientAlias).payload(request).build());
    }

    private String initiateTransaction(int planId, String uid, String msisdn, PaymentCode paymentCode) {
        PlanDTO selectedPlan = cachingService.getPlan(planId);
        TransactionContext.set(transactionManager.initiateTransaction(TransactionInitRequest.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .paymentCode(paymentCode)
                .amount(selectedPlan.getFinalPrice())
                .status(TransactionStatus.INPROGRESS.getValue())
                .event(PaymentEvent.RENEW)
                .build()));
        return TransactionContext.get().getId().toString();
    }

    private Transaction initiateTransactionForRenew(int planId, String uid, String msisdn, String clientAlias, PaymentCode paymentCode) {
        PlanDTO selectedPlan = cachingService.getPlan(planId);
        final double finalAmountToBePaid = selectedPlan.getFinalPrice();
        final TransactionInitRequest request = TransactionInitRequest.builder().uid(uid).msisdn(msisdn)
                .paymentCode(paymentCode).clientAlias(clientAlias).planId(planId).event(PaymentEvent.RENEW)
                .amount(finalAmountToBePaid).build();
        TransactionContext.set(transactionManager.initiateTransaction(request));
        return TransactionContext.get();
    }

    private Transaction initiateTransactionForPlan(boolean freeTrial, int planId, String uid, String msisdn, PaymentCode paymentCode) {
        final SessionDTO session = SessionContextHolder.getBody();
        PlanDTO selectedPlan = cachingService.getPlan(planId);
        PaymentEvent paymentEvent = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? PaymentEvent.PURCHASE : PaymentEvent.SUBSCRIBE;
        double finalAmountToBePaid = selectedPlan.getFinalPrice();
        if (freeTrial) {
            paymentEvent = PaymentEvent.TRIAL_SUBSCRIPTION;
            PlanDTO trialPlan = cachingService.getPlan(selectedPlan.getLinkedFreePlanId());
            finalAmountToBePaid = trialPlan.getFinalPrice();
        }
        TransactionContext.set(transactionManager.initiateTransaction(TransactionInitRequest.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .event(paymentEvent)
                .paymentCode(paymentCode)
                .amount(finalAmountToBePaid)
                .clientAlias(session.get(CLIENT))
                .status(TransactionStatus.INPROGRESS.getValue())
                .build()));
        return TransactionContext.get();
    }

    private Transaction initiateRefundTransaction(String uid, String msisdn, int planId, String itemId, String clientAlias, double amount, PaymentCode paymentCode) {
        final TransactionInitRequest.TransactionInitRequestBuilder builder = TransactionInitRequest.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .itemId(itemId)
                .amount(amount)
                .paymentCode(paymentCode)
                .clientAlias(clientAlias)
                .event(PaymentEvent.REFUND);
        TransactionContext.set(transactionManager.initiateTransaction(builder.build()));
        return TransactionContext.get();
    }

    private Transaction initiateTransaction(boolean autoRenew, int planId, String uid, String msisdn, String itemId, String couponId, PaymentCode paymentCode) {
        final CouponDTO coupon;
        final double amountToBePaid;
        double finalAmountToBePaid;
        final SessionDTO session = SessionContextHolder.getBody();
        final String service = session.get(SERVICE);
        final String clientAlias = session.get(CLIENT);
        final TransactionInitRequest.TransactionInitRequestBuilder builder = TransactionInitRequest.builder().uid(uid).msisdn(msisdn).paymentCode(paymentCode).clientAlias(clientAlias);
        PlanDTO selectedPlan = cachingService.getPlan(planId);
        PaymentEvent paymentEvent;
        if (StringUtils.isNotEmpty(itemId)) {
            builder.itemId(itemId);
            paymentEvent = PaymentEvent.POINT_PURCHASE;
            amountToBePaid = session.get(BaseConstants.POINT_PURCHASE_ITEM_PRICE);
            coupon = getCoupon(couponId, msisdn, uid, service, itemId, paymentCode, null);
        } else {
            builder.planId(planId);
            amountToBePaid = selectedPlan.getFinalPrice();
            paymentEvent = autoRenew ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE;
            coupon = getCoupon(couponId, msisdn, uid, service, null, paymentCode, selectedPlan);
        }
        if (coupon != null) {
            builder.couponId(couponId).discount(coupon.getDiscountPercent());
            finalAmountToBePaid = getFinalAmount(amountToBePaid, coupon);
        } else {
            finalAmountToBePaid = amountToBePaid;
        }
        Set<Integer> eligiblePlans = session.get(ELIGIBLE_PLANS);
        if (Objects.nonNull(eligiblePlans) && eligiblePlans.contains(selectedPlan.getLinkedFreePlanId())) {
            paymentEvent = PaymentEvent.TRIAL_SUBSCRIPTION;
            PlanDTO trialPlan = cachingService.getPlan(selectedPlan.getLinkedFreePlanId());
            finalAmountToBePaid = trialPlan.getFinalPrice();
        }
        session.put(ELIGIBLE_PLANS, new HashSet<>());
        builder.event(paymentEvent).amount(finalAmountToBePaid).build();
        TransactionContext.set(transactionManager.initiateTransaction(builder.build()));
        return TransactionContext.get();
    }

    private CouponDTO getCoupon(String couponId, String msisdn, String uid, String service, String
            itemId, PaymentCode paymentCode, PlanDTO selectedPlan) {
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

    private void exhaustCouponIfApplicable(TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        if (existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            if (StringUtils.isNotEmpty(transaction.getCoupon())) {
                try {
                    couponManager.exhaustCoupon(transaction.getUid(), transaction.getCoupon());
                } catch (WynkRuntimeException e) {
                    log.error(e.getMarker(), e.getMessage(), e);
                }
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

    private void publishEventsOnReconcileCompletion(TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

}
