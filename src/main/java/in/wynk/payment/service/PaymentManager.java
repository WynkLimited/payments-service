package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.BeanLocatorFactory;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.TransactionInitRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.queue.service.ISqsManagerService;
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
        final Transaction transaction = initiateTransaction(uid, msisdn, request);
        TransactionContext.set(transaction);
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        sqsManagerService.publishSQSMessage(reconciliationMessage);
        return baseResponse;
    }

    private Transaction initiateTransaction(String uid, String msisdn, ChargingRequest request) {
        final int planId = request.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        PaymentCode paymentCode = request.getPaymentCode();
        TransactionInitRequest.TransactionInitRequestBuilder transactionInitRequestBuilder = TransactionInitRequest.builder()
                .msisdn(msisdn).paymentCode(paymentCode).planId(planId).uid(uid);
        final double finalPlanAmount;
        if(StringUtils.isNotEmpty(request.getCouponId())) {
            Coupon coupon = getCoupon(request.getCouponId(), msisdn, uid, paymentCode, selectedPlan);
            transactionInitRequestBuilder.couponId(coupon.getId()).discount(coupon.getDiscountPercent());
            finalPlanAmount = getFinalAmount(selectedPlan, coupon);
        } else{
            finalPlanAmount = selectedPlan.getFinalPrice();
        }
        final TransactionEvent eventType = request.isAutoRenew() ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE;
        TransactionInitRequest transactionInitRequest = transactionInitRequestBuilder.amount(finalPlanAmount).event(eventType).build();
        return transactionManager.initiateTransaction(transactionInitRequest);
    }

    private Coupon getCoupon(String couponId, String msisdn,String uid, PaymentCode paymentCode, PlanDTO selectedPlan) {
        CouponProvisionRequest couponProvisionRequest = CouponProvisionRequest.builder()
                .couponCode(couponId).msisdn(msisdn).paymentCode(paymentCode.getCode()).selectedPlan(selectedPlan).uid(uid).build();
        CouponResponse couponResponse = couponManager.applyCoupon(couponProvisionRequest);
        return couponResponse.getCoupon();
    }

    private double getFinalAmount(PlanDTO selectedPlan, Coupon coupon) {
        final double planAmount = selectedPlan.getFinalPrice();
        double discount = coupon.getDiscountPercent();
        return planAmount - (planAmount * discount) / 100;
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

    private void exhaustCouponIfApplicable() {
        Transaction transaction = TransactionContext.get();
        if(StringUtils.isNotEmpty(transaction.getCoupon())) {
            couponManager.exhaustCoupon(transaction.getUid(), transaction.getCoupon());
        }
    }

    @TransactionAware(txnId = "#request.getTransactionId()")
    public BaseResponse<?> status(ChargingStatusRequest request, PaymentCode paymentCode, boolean isSync) {
        IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        final Transaction transaction = TransactionContext.get();
        TransactionStatus existingStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = statusService.status(request);
        TransactionStatus finalStatus = ((ChargingStatusResponse) baseResponse.getBody()).getTransactionStatus();
        if(isSync) {
            transactionManager.updateAndSyncPublish(transaction, existingStatus, finalStatus);
        } else {
            transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
        }
        return baseResponse;
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        IMerchantVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        return verificationService.doVerify(request);
    }

    public void doRenewal(PaymentRenewalChargingMessage message) {
        IMerchantPaymentRenewalService merchantPaymentRenewalService = BeanLocatorFactory.getBean(message.getPaymentCode().getCode(), IMerchantPaymentRenewalService.class);
        merchantPaymentRenewalService.doRenewal(message.getPaymentRenewalRequest());
    }
}
