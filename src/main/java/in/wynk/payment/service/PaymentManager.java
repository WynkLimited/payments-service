package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.utils.BeanLocatorFactory;
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
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
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

    public BaseResponse<?> doCharging(ChargingRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        final Transaction transaction = initiateTransaction(request, paymentCode);
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        BaseResponse<?> baseResponse = chargingService.doCharging(request, transaction);
        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, reconciliationMessage);
        return baseResponse;
    }

    private Transaction initiateTransaction(ChargingRequest request, PaymentCode paymentCode) {
        //TODO: Remove session dependency
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final int planId = request.getPlanId();
        final String uid = sessionDTO.get(SessionKeys.UID);
        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));
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

    public BaseResponse<?> handleCallback(CallbackRequest request) {
        final String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID).toString();
        final Transaction transaction = transactionManager.get(transactionId);
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        AnalyticService.update(SessionKeys.PAYMENT_CODE, paymentCode.name());
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        BaseResponse<?> baseResponse = callbackService.handleCallback(request, transaction);
        return baseResponse;
    }

    private <T> T getValueFromSession(String key) {
        Session<SessionDTO> session = SessionContextHolder.get();
        return session.getBody().get(key);
    }

    public BaseResponse<?> status(ChargingStatusRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        PaymentCode paymentCode = PaymentCode.getFromCode(sessionDTO.get(SessionKeys.PAYMENT_CODE));
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        IMerchantPaymentStatusService statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        final Transaction transaction = transactionManager.get(request.getTransactionId());
        TransactionStatus existingStatus = transaction.getStatus();
        BaseResponse<?> baseResponse = statusService.status(request, transaction);
        ChargingStatusResponse chargingStatusResponse = (ChargingStatusResponse) baseResponse.getBody();
        TransactionStatus finalStatus = chargingStatusResponse.getTransactionStatus();
        transactionManager.updateAndAsyncPublish(transaction, existingStatus, finalStatus);
        return baseResponse;
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        IMerchantVerificationService verificationService;
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        BaseResponse<?> baseResponse = verificationService.doVerify(request);
        return baseResponse;
    }
}
