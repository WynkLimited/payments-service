package in.wynk.payment.scheduler;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dto.PaymentRenewalMessage;
import in.wynk.payment.service.ISqsManagerService;
import in.wynk.payment.service.impl.RecurringPaymentManagerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;
import java.util.stream.Collectors;



public class PaymentRenewalsScheduler {


    @Autowired
    private RecurringPaymentManagerManager recurringPaymentManager;
    @Autowired
    private ISqsManagerService sqsManagerService;

    @Scheduled(cron = "0 * * * *")
    public void paymentRenew(){
        List<PaymentRenewal> paymentRenewals =
                recurringPaymentManager
                    .getCurrentDueRecurringPayments()
                    .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == TransactionEvent.RENEW || paymentRenewal.getTransactionEvent() == TransactionEvent.SUBSCRIBE))
                    .collect(Collectors.toList());

        sendToRenewalQueue(paymentRenewals);

    }
    private void sendToRenewalQueue(List<PaymentRenewal> paymentRenewals){
        for(PaymentRenewal paymentRenewal : paymentRenewals){
            sqsManagerService.publishSQSMessage(new PaymentRenewalMessage(paymentRenewal));
        }
    }

}
