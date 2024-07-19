package in.wynk.payment.scheduler;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.data.aspect.advice.Transactional;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.UnScheduleRecurringPaymentEvent;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.pubsub.service.IPubSubManagerService;
import in.wynk.queue.service.ISqsManagerService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.common.enums.PaymentEvent.*;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@Service
@Slf4j
public class PaymentRenewalsScheduler {

    @Autowired
    private IRecurringPaymentManagerService recurringPaymentManager;
    @Autowired
    private ISqsManagerService sqsManagerService;
    @Autowired
    private IPubSubManagerService pubSubManagerService;
    @Autowired
    private SeRenewalService seRenewalService;
    @Autowired
    private ITransactionManagerService transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${payment.predebit.unsupported}")
    private List<String> preDebitUnSupportedPG;
    @Value("${payment.renewal.unsupported}")
    private List<String> renewalUnSupportedPG;

    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "paymentRenewals")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void paymentRenew(String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("paymentRenewalsInit", true);
        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getCurrentDueRecurringPayments(clientAlias)
                .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED))
                .collect(Collectors.toList());
        sendToRenewalQueue(paymentRenewals);
        AnalyticService.update("paymentRenewalsCompleted", true);
    }

    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "renewNotifications")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void sendNotifications(String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("renewNotificationsInit", true);
        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getCurrentDueNotifications(clientAlias)
                .filter(paymentRenewal -> checkPreDebitEligibility(paymentRenewal.getTransactionId()) &&
                        (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED))
                .collect(Collectors.toList());
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        paymentRenewals.forEach(paymentRenewal -> publishPreDebitNotificationMessage(
                PreDebitNotificationMessage.builder().transactionId(paymentRenewal.getTransactionId()).date(format.format(paymentRenewal.getDay().getTime())).build()));
        AnalyticService.update("renewNotificationsCompleted", true);
    }

    private void sendToRenewalQueue(List<PaymentRenewal> paymentRenewals) {
        for (PaymentRenewal paymentRenewal : paymentRenewals) {
            publishRenewalMessage(PaymentRenewalMessage.builder()
                    .attemptSequence(paymentRenewal.getAttemptSequence())
                    .transactionId(paymentRenewal.getTransactionId())
                    .paymentEvent(paymentRenewal.getTransactionEvent())
                    .build());
        }
    }

    @AnalyseTransaction(name = "scheduleRenewalMessage")
    private void publishRenewalMessage (PaymentRenewalMessage message) {
        AnalyticService.update(message);
        if (checkRenewalEligibility(message.getTransactionId(), message.getAttemptSequence())) {
            //sqsManagerService.publishSQSMessage(message);
            pubSubManagerService.publishPubSubMessage(message);
        }
    }

    private boolean checkRenewalEligibility (String transactionId, int attemptSequence) {
        Transaction transaction = transactionManager.get(transactionId);
        if ((transaction.getStatus() == TransactionStatus.FAILURE && attemptSequence >= PaymentConstants.MAXIMUM_RENEWAL_RETRY_ALLOWED) ||
                (transaction.getStatus() == TransactionStatus.FAILURE && transaction.getType() != RENEW) || (transaction.getStatus() == TransactionStatus.CANCELLED)) {
            try {
                eventPublisher.publishEvent(UnScheduleRecurringPaymentEvent.builder().transactionId(transaction.getIdStr()).clientAlias(transaction.getClientAlias())
                        .reason("Stopping Payment Renewal because transaction status is " + transaction.getStatus().getValue()).build());
            } catch (Exception e) {
                return false;
            }
            return false;
        }
        return !renewalUnSupportedPG.contains(transaction.getPaymentChannel().getId());
    }

    @AnalyseTransaction(name = "schedulePreDebitNotificationMessage")
    private void publishPreDebitNotificationMessage(PreDebitNotificationMessage message) {
        AnalyticService.update(message);
        if(checkPreDebitEligibility(message.getTransactionId())) {
            //sqsManagerService.publishSQSMessage(message);
            pubSubManagerService.publishPubSubMessage(message);
        }
    }

    private boolean checkPreDebitEligibility (String transactionId) {
        return !preDebitUnSupportedPG.contains(transactionManager.get(transactionId).getPaymentChannel().getId());
    }

    @ClientAware(clientId = "#clientId")
    @AnalyseTransaction(name = "sePaymentRenewals")
    public void startSeRenewals(String requestId, String clientId) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("sePaymentRenewalsInit", true);
        seRenewalService.startSeRenewal();
        AnalyticService.update("sePaymentRenewalsCompleted", true);
    }

}
