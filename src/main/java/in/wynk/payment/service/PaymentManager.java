package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.BeanLocatorFactory;
import in.wynk.coupon.core.constant.CouponProvisionState;
import in.wynk.coupon.core.constant.ProvisionSource;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Abhishek
 * @created 07/08/20
 */
@Slf4j
@Service
public class PaymentManager {

    @Autowired
    private ITransactionManagerService transactionManager;

    @Autowired
    private PaymentCachingService cachingService;

    @Autowired
    private ISqsManagerService sqsManagerService;

    @Autowired
    private ICouponManager couponManager;

    public BaseResponse<?> doCharging(String uid, String msisdn, ChargingRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = initiateTransaction(request.getPlanId(), request.isAutoRenew(), uid, msisdn, request.getCouponId(), paymentCode);
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        sqsManagerService.publishSQSMessage(reconciliationMessage);
        return baseResponse;
    }

    @TransactionAware(txnId = "#transactionId")
    public BaseResponse<?> handleCallback(String transactionId, CallbackRequest request, PaymentCode paymentCode) {
        Transaction transaction = TransactionContext.get();
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        TransactionStatus initialTxnStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        TransactionStatus finalStatus = TransactionContext.get().getStatus();
        transactionManager.updateAndSyncPublish(transaction, initialTxnStatus, finalStatus);
        if(finalStatus == TransactionStatus.SUCCESS){
            exhaustCouponIfApplicable();
        }
        return baseResponse;
    }

    @TransactionAware(txnId = "#request.getTransactionId()")
    public BaseResponse<?> status(ChargingStatusRequest request, PaymentCode paymentCode, boolean isSync) {
        IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        final Transaction transaction = TransactionContext.get();
        TransactionStatus existingStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = statusService.status(request);
        TransactionStatus finalStatus = ((ChargingStatusResponse) baseResponse.getBody()).getTransactionStatus();
        if (isSync) {
            transactionManager.updateAndSyncPublish(transaction, existingStatus, finalStatus);
        } else {
            transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
        }
        if (finalStatus == TransactionStatus.SUCCESS) {
            exhaustCouponIfApplicable();
        }
        return baseResponse;
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        IMerchantVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        return verificationService.doVerify(request);
    }

    public BaseResponse<?> doVerifyIap(IapVerificationRequest request) {
        final PaymentCode paymentCode = request.paymentCode();
        final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
        final boolean autoRenew = selectedPlan.getPlanType() == PlanType.SUBSCRIPTION ? true: false;
        final Transaction transaction = initiateTransaction(request.getPlanId(), autoRenew, request.getUid(), request.getMsisdn(), null, paymentCode);
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

    private Transaction initiateTransaction(int planId, boolean autoRenew, String uid, String msisdn, String couponId, PaymentCode paymentCode) {
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        double finalPlanAmount = selectedPlan.getFinalPrice();
        Coupon coupon = getCoupon(couponId, msisdn, uid, paymentCode, selectedPlan);
        TransactionInitRequest.TransactionInitRequestBuilder builder = TransactionInitRequest.builder().uid(uid).msisdn(msisdn).planId(planId).paymentCode(paymentCode);
        if (coupon != null) {
            builder.couponId(coupon.getId()).discount(coupon.getDiscountPercent());
            finalPlanAmount = getFinalAmount(selectedPlan, coupon);
        }
        final TransactionEvent eventType = autoRenew ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE;
        builder.amount(finalPlanAmount).event(eventType).build();
        TransactionContext.set(transactionManager.initiateTransaction(builder.build()));
        return TransactionContext.get();
    }

    private Coupon getCoupon(String couponId, String msisdn, String uid, PaymentCode paymentCode, PlanDTO selectedPlan) {
        if (!StringUtils.isEmpty(couponId)) {
            CouponProvisionRequest couponProvisionRequest = CouponProvisionRequest.builder()
                    .couponCode(couponId).msisdn(msisdn).paymentCode(paymentCode.getCode()).selectedPlan(selectedPlan).uid(uid).source(ProvisionSource.MANAGED).build();
            CouponResponse couponResponse = couponManager.evalCouponEligibility(couponProvisionRequest);
            return couponResponse.getState() != CouponProvisionState.INELIGIBLE ? couponResponse.getCoupon() : null;
        } else {
            return null;
        }
    }

    private double getFinalAmount(PlanDTO selectedPlan, Coupon coupon) {
        final double planAmount = selectedPlan.getFinalPrice();
        double discount = coupon.getDiscountPercent();
        return planAmount - (planAmount * discount) / 100;
    }

    private void exhaustCouponIfApplicable() {
        Transaction transaction = TransactionContext.get();
        if(StringUtils.isNotEmpty(transaction.getCoupon())) {
            couponManager.exhaustCoupon(transaction.getUid(), transaction.getCoupon());
        }
    }

}
