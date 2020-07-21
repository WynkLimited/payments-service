package in.wynk.payment.scheduler;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISqsManagerService;
import org.apache.commons.lang3.StringUtils;
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
                    .filter(paymentRenewal -> (paymentRenewal.getTransactionEvent() == TransactionEvent.RENEW || paymentRenewal.getTransactionEvent() == TransactionEvent.SUBSCRIBE))
                    .collect(Collectors.toList());
        sendToRenewalQueue(paymentRenewals);

    }
    private void sendToRenewalQueue(List<PaymentRenewal> paymentRenewals){
        for(PaymentRenewal paymentRenewal : paymentRenewals){
            sqsManagerService.publishSQSMessage(new PaymentRenewalMessage(paymentRenewal), StringUtils.EMPTY);
        }
    }

}
