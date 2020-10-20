package in.wynk.payment.scheduler;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.queue.service.ISqsManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentRenewalsScheduler {


    @Autowired
    private IRecurringPaymentManagerService recurringPaymentManager;

    @Autowired
    private ISqsManagerService sqsManagerService;

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void paymentRenew(){
        List<PaymentRenewal> paymentRenewals =
                recurringPaymentManager.getCurrentDueRecurringPayments()
                        .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == PaymentEvent.RENEW || paymentRenewal.getTransactionEvent() == PaymentEvent.SUBSCRIBE))
                    .collect(Collectors.toList());
        sendToRenewalQueue(paymentRenewals);

    }
    private void sendToRenewalQueue(List<PaymentRenewal> paymentRenewals){
        for(PaymentRenewal paymentRenewal : paymentRenewals){
            sqsManagerService.publishSQSMessage(PaymentRenewalMessage.builder()
                    .transactionId(paymentRenewal.getTransactionId())
                    .paymentEvent(paymentRenewal.getTransactionEvent())
                    .build());
        }
    }

}
