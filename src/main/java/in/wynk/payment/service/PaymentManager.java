package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.BeanLocatorFactory;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.queue.service.ISqsManagerService;
import lombok.extern.slf4j.Slf4j;
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

    public BaseResponse<?> doCharging(ChargingRequest request, String uid, String msisdn) {
        PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = initiateTransaction(request, paymentCode, uid, msisdn);
        TransactionContext.set(transaction);
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        sqsManagerService.publishSQSMessage(reconciliationMessage);
        return baseResponse;
    }

    private Transaction initiateTransaction(ChargingRequest request, PaymentCode paymentCode, String uid, String msisdn) {
        final int planId = request.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        final double finalPlanAmount = selectedPlan.getFinalPrice();
        final TransactionEvent eventType = request.isAutoRenew() ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE;
        return transactionManager.initiateTransaction(uid, msisdn, planId, finalPlanAmount, paymentCode, eventType);
    }

    @TransactionAware(txnId = "#transactionId")
    public BaseResponse<?> handleCallback(CallbackRequest request, PaymentCode paymentCode, String transactionId) {
        Transaction transaction = TransactionContext.get();
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        TransactionStatus initialTxnStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        TransactionStatus finalStatus = TransactionContext.get().getStatus();
        transactionManager.updateAndSyncPublish(transaction, initialTxnStatus, finalStatus);
        return baseResponse;
    }

    @TransactionAware(txnId = "#request.getTransactionId()")
    public BaseResponse<?> status(ChargingStatusRequest request, PaymentCode paymentCode) {
        IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        final Transaction transaction = transactionManager.get(request.getTransactionId());
        TransactionContext.set(transaction);
        TransactionStatus existingStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = statusService.status(request);
        TransactionStatus finalStatus = ((ChargingStatusResponse) baseResponse.getBody()).getTransactionStatus();
        transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
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
