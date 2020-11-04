package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.subscription.common.dto.PlanDTO;

import java.util.List;

public interface ISubscriptionServiceManager {

    void subscribePlanAsync(int planId, String transactionId, String uid, String msisdn, String paymentCode, TransactionStatus transactionStatus, PaymentEvent paymentEvent);

    void unSubscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus);

    void subscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, String paymentCode, TransactionStatus transactionStatus, PaymentEvent paymentEvent);

    void unSubscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus);

    List<PlanDTO> getPlans();

    

}
