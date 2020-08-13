package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;

import java.util.List;

public interface ISubscriptionServiceManager {

    void subscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus, TransactionEvent transactionEvent);

    void unSubscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus);

    void subscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus, TransactionEvent transactionEvent);

    void unSubscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus);

    List<PlanDTO> getPlans();

    

}
