package in.wynk.payment.scheduler;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.data.aspect.advice.Transactional;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.utils.RecurringTransactionUtils;
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
    private SeRenewalService seRenewalService;
    @Autowired
    private ITransactionManagerService transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private RecurringTransactionUtils recurringTransactionUtils;

    @Value("${payment.predebit.unsupported}")
    private List<String> preDebitUnSupportedPG;

    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "paymentRenewals")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void paymentRenew(String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("clientAlias", clientAlias);
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
                PreDebitNotificationMessage.builder().transactionId(paymentRenewal.getTransactionId()).date(format.format(paymentRenewal.getDay().getTime()))
                        .initialTransactionId(paymentRenewal.getInitialTransactionId()).lastSuccessTransactionId(paymentRenewal.getLastSuccessTransactionId()).build()));
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
        if (recurringTransactionUtils.checkRenewalEligibility(message.getTransactionId(), message.getAttemptSequence())) {
            sqsManagerService.publishSQSMessage(message);
        }
    }

    @AnalyseTransaction(name = "schedulePreDebitNotificationMessage")
    private void publishPreDebitNotificationMessage(PreDebitNotificationMessage message) {
        AnalyticService.update(message);
        sqsManagerService.publishSQSMessage(message);
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
