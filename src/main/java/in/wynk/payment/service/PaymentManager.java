package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.utils.BeanLocatorFactory;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

/**
 * @author Abhishek
 * @created 07/08/20
 */
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

    public BaseResponse<?> doCharging(ChargingRequest request) {
        PaymentCode paymentCode = request.getPaymentCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final int planId = request.getPlanId();
        final String uid = sessionDTO.get(SessionKeys.UID);
        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        final double finalPlanAmount = selectedPlan.getFinalPrice();
        final TransactionEvent eventType = request.isAutoRenew() ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE;
        final Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, request.getPlanId(), finalPlanAmount, PaymentCode.PAYU, eventType);
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, reconciliationMessage);
        return chargingService.doCharging(request, transaction);
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

}
