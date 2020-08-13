package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

/**
 * @author Abhishek
 * @created 07/08/20
 */
@Slf4j
@Service
public class PaymentManager {

    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;

    @Autowired
    private ITransactionManagerService transactionManager;

    @Autowired
    private PaymentCachingService cachingService;

    @Autowired
    private ISQSMessagePublisher sqsMessagePublisher;

    /**
     * TODO:
     * fetch tid from session
     * fetch txn from tid
     * put tid into context
     * create aspect for the above
     */

    public BaseResponse<?> doCharging(ChargingRequest request, String uid, String msisdn) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        final Transaction transaction = initiateTransaction(request, paymentCode, uid, msisdn);
        TransactionContext.set(transaction);
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, reconciliationMessage);
        return baseResponse;
    }

    private Transaction initiateTransaction(ChargingRequest request, PaymentCode paymentCode, String uid, String msisdn) {
        final int planId = request.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        final double finalPlanAmount = selectedPlan.getFinalPrice();
        final TransactionEvent eventType = request.isAutoRenew() ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE;
        return transactionManager.initiateTransaction(uid, msisdn, planId, finalPlanAmount, paymentCode, eventType);
    }

    private <T> void publishSQSMessage(String queueName, int messageDelay, T message) {
        try {
            sqsMessagePublisher.publish(SendSQSMessageRequest.<T>builder()
                    .queueName(queueName)
                    .delaySeconds(messageDelay)
                    .message(message)
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

    public BaseResponse<?> handleCallback(CallbackRequest request, PaymentCode paymentCode) {
        Transaction transaction = TransactionContext.get();
        AnalyticService.update(SessionKeys.PAYMENT_CODE, paymentCode.name());
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        TransactionStatus initialTxnStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        TransactionStatus finalStatus = TransactionContext.get().getStatus();
        transactionManager.updateAndSyncPublish(transaction, initialTxnStatus, finalStatus);
        return baseResponse;
    }

    public BaseResponse<?> status(ChargingStatusRequest request, PaymentCode paymentCode) {
        IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        final Transaction transaction = transactionManager.get(request.getTransactionId());
        TransactionContext.set(transaction);
        TransactionStatus existingStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = statusService.status(request);
        ChargingStatusResponse chargingStatusResponse = (ChargingStatusResponse) baseResponse.getBody();
        TransactionStatus finalStatus = chargingStatusResponse.getTransactionStatus();
        transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
        return baseResponse;
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        IMerchantVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        BaseResponse<?> baseResponse = verificationService.doVerify(request);
        return baseResponse;
    }
}
