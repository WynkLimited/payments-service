package in.wynk.payment.scheduler;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.data.aspect.advice.Transactional;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.dto.PreDebitNotificationMessageManager;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.stream.producer.IKafkaPublisherService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.enums.PaymentEvent.*;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@Service
@Slf4j
public class PaymentRenewalsScheduler {

    @Autowired
    private IRecurringPaymentManagerService recurringPaymentManager;
    @Autowired
    private IKafkaPublisherService kafkaPublisherService;
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
    public void paymentRenew (String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("clientAlias", clientAlias);
        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getCurrentDueRecurringPayments(clientAlias)
                .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED))
                .collect(Collectors.toList());
        List<PaymentRenewal> renewals = filterbyLastSuccessTransaction(paymentRenewals);
        sendToRenewalQueue(renewals);
        AnalyticService.update("paymentRenewalsCompleted", true);
    }
    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "customPaymentRenewals")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void paymentRenewCustom(String requestId, String clientAlias, int offsetDay, int offsetTime, int preOffsetDays) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getCurrentDueRecurringPayments(clientAlias, offsetDay, offsetTime, preOffsetDays)
                        .filter(p -> (p.getTransactionEvent() == RENEW || p.getTransactionEvent() == SUBSCRIBE || p.getTransactionEvent() == DEFERRED))
                        .collect(Collectors.toList());
        List<PaymentRenewal> renewals = filterbyLastSuccessTransaction(paymentRenewals);
        sendToRenewalQueue(renewals);
    }

    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "prepareNextDayRenewals")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void prepareNextDayRenewals(String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("clientAlias", clientAlias);
        AnalyticService.update("executionPurpose", "NPCI_Compliant_NextDay_Window_Update");

        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getNextDayRecurringPayments(clientAlias)
                .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == RENEW ||
                        paymentRenewal.getTransactionEvent() == SUBSCRIBE ||
                        paymentRenewal.getTransactionEvent() == DEFERRED))
                .collect(Collectors.toList());

        sendToNextDayRenewalHourFixingQueue(paymentRenewals);
        AnalyticService.update("transactionsSize", paymentRenewals.size());
    }

    private List<PaymentRenewal> filterbyLastSuccessTransaction(List<PaymentRenewal> paymentRenewals){
        if (paymentRenewals == null || paymentRenewals.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> duplicateTransactions = new HashSet<>();
        List<PaymentRenewal> distinctRenewals = new ArrayList<>();

        for (PaymentRenewal renewal : paymentRenewals) {
            String lastSuccessTransactionId = renewal.getLastSuccessTransactionId();
            if (lastSuccessTransactionId == null || duplicateTransactions.add(lastSuccessTransactionId)) {
                distinctRenewals.add(renewal);
            }
        }
        return distinctRenewals;

    }

    private void sendToNextDayRenewalHourFixingQueue(List<PaymentRenewal> paymentRenewals){
        for (PaymentRenewal paymentRenewal : paymentRenewals) {
            try {
                Calendar originalHourCal = Calendar.getInstance();
                originalHourCal.setTime(paymentRenewal.getHour());

                recurringPaymentManager.scheduleToNonPeakHours(originalHourCal);

                Date updatedHour = originalHourCal.getTime();

                if (!updatedHour.equals(paymentRenewal.getHour())) {
                    log.info("Updating renewal hour for txnId={} from {} to {}",
                            paymentRenewal.getTransactionId(),
                            paymentRenewal.getHour(),
                            updatedHour);

                    paymentRenewal.setHour(updatedHour);
                    paymentRenewal.setUpdatedTimestamp(Calendar.getInstance());
                    recurringPaymentManager.upsert(paymentRenewal);
                }
            } catch (Exception e) {
                log.error("Error processing PaymentRenewal with txnId : {}: {}",
                        paymentRenewal.getTransactionId(), e.getMessage(), e);
            }
        }
        AnalyticService.update("nextDayPaymentRenewalsPrepared", true);
    }


    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "renewNotifications")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void sendNotifications (String requestId, String clientAlias) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("clientAlias", clientAlias);
        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getCurrentDueNotifications(clientAlias)
                .filter(paymentRenewal -> checkPreDebitEligibility(paymentRenewal.getTransactionId()) &&
                        (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED))
                .collect(Collectors.toList());
        AnalyticService.update("transactionsPickedSize", paymentRenewals.size());
        List<PaymentRenewal> renewals = filterbyLastSuccessTransaction(paymentRenewals);
        AnalyticService.update("transactionsSizeAfterLastSuccessFilter", renewals.size());
        renewals.forEach(paymentRenewal -> publishPreDebitNotificationMessage(
                PreDebitNotificationMessageManager.builder().clientAlias(clientAlias).transactionId(paymentRenewal.getTransactionId()).build()));
        AnalyticService.update("renewNotificationsCompleted", true);
    }
    @ClientAware(clientAlias = "#clientAlias")
    @AnalyseTransaction(name = "customRenewNotifications")
    @Transactional(transactionManager = "#clientAlias", source = "payments")
    public void sendNotificationsCustom(String requestId, String clientAlias, int offsetDay, int offsetTime, int preOffsetDays) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("clientAlias", clientAlias);

        List<PaymentRenewal> paymentRenewals = recurringPaymentManager.getCurrentDueNotifications(clientAlias, offsetDay, offsetTime, preOffsetDays)
                        .filter(paymentRenewal -> checkPreDebitEligibility(paymentRenewal.getTransactionId()) && (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED))
                        .collect(Collectors.toList());

        AnalyticService.update("transactionsPickedSize", paymentRenewals.size());
        List<PaymentRenewal> renewals = filterbyLastSuccessTransaction(paymentRenewals);
        AnalyticService.update("transactionsSizeAfterLastSuccessFilter", renewals.size());

        renewals.forEach(r -> publishPreDebitNotificationMessage(
                PreDebitNotificationMessageManager.builder().clientAlias(clientAlias).transactionId(r.getTransactionId()).build())
        );
        AnalyticService.update("renewNotificationsCompleted", true);
    }

    private void sendToRenewalQueue (List<PaymentRenewal> paymentRenewals) {
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
            //sqsManagerService.publishSQSMessage(message);
            kafkaPublisherService.publishKafkaMessage(message);
        }
    }

    @AnalyseTransaction(name = "schedulePreDebitNotificationMessage")
    private void publishPreDebitNotificationMessage (PreDebitNotificationMessageManager message) {
        AnalyticService.update(message);
        //sqsManagerService.publishSQSMessage(message);
        kafkaPublisherService.publishKafkaMessage(message);
    }

    private boolean checkPreDebitEligibility (String transactionId) {
        return !preDebitUnSupportedPG.contains(transactionManager.get(transactionId).getPaymentChannel().getId());
    }

    @ClientAware(clientId = "#clientId")
    @AnalyseTransaction(name = "sePaymentRenewals")
    public void startSeRenewals (String requestId, String clientId) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("sePaymentRenewalsInit", true);
        seRenewalService.startSeRenewal();
        AnalyticService.update("sePaymentRenewalsCompleted", true);
    }

}
