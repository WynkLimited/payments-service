package in.wynk.payment.scheduler;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.queue.service.ISqsManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@Service
public class PaymentRenewalsScheduler {


    @Autowired
    private IRecurringPaymentManagerService recurringPaymentManager;

    @Autowired
    private ISqsManagerService sqsManagerService;
    @Autowired
    private SeRenewalService seRenewalService;

    //@Scheduled(cron = "0 0 * * * ?")
    @Transactional
    @AnalyseTransaction(name = "paymentRenewals")
    public void paymentRenew(String requestId) {
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("paymentRenewalsInit", true);
        List<PaymentRenewal> paymentRenewals =
                recurringPaymentManager.getCurrentDueRecurringPayments()
                        .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == PaymentEvent.RENEW || paymentRenewal.getTransactionEvent() == PaymentEvent.SUBSCRIBE))
                        .collect(Collectors.toList());
        sendToRenewalQueue(paymentRenewals);
        AnalyticService.update("paymentRenewalsCompleted", true);
    }

    private void sendToRenewalQueue(List<PaymentRenewal> paymentRenewals) {
        for (PaymentRenewal paymentRenewal : paymentRenewals) {
            publishRenewalMessage(PaymentRenewalMessage.builder()
                    .transactionId(paymentRenewal.getTransactionId())
                    .paymentEvent(paymentRenewal.getTransactionEvent())
                    .build());
        }
    }

    @AnalyseTransaction(name = "scheduleRenewalMessage")
    private void publishRenewalMessage(PaymentRenewalMessage message) {
        AnalyticService.update(message);
        sqsManagerService.publishSQSMessage(message);
    }

    //    @Scheduled(cron = "0 0 2 * * ?")
    @AnalyseTransaction(name = "sePaymentRenewals")
    public void startSeRenewals(String requestId) {
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("sePaymentRenewalsInit", true);
        seRenewalService.startSeRenewal();
        AnalyticService.update("sePaymentRenewalsCompleted", true);
    }

}
