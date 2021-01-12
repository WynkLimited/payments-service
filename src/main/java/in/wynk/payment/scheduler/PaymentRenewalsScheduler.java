package in.wynk.payment.scheduler;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.queue.service.ISqsManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static in.wynk.common.enums.PaymentEvent.*;

@Service
public class PaymentRenewalsScheduler {

    @Autowired
    private IRecurringPaymentManagerService recurringPaymentManager;
    @Autowired
    private ISqsManagerService sqsManagerService;
    @Autowired
    private SeRenewalService seRenewalService;
    @Autowired
    private ExecutorService executorService;

    @Transactional
    public void paymentRenew() {
        List<PaymentRenewal> paymentRenewals =
                recurringPaymentManager.getCurrentDueRecurringPayments()
                        .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED))
                        .collect(Collectors.toList());
        sendToRenewalQueue(paymentRenewals);
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

    public void startSeRenewals() {
        executorService.submit(() -> seRenewalService.startSeRenewal());
    }

}
