package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.enums.WynkService;

import java.util.List;

public interface ISubscriptionServiceManager {

    void subscribePlanAsync(int planId, String transactionId, String uid, String msisdn, WynkService service, TransactionStatus transactionStatus, TransactionEvent transactionEvent);

    void unSubscribePlanAsync(int planId, String transactionId, String uid, String msisdn, WynkService service, TransactionStatus transactionStatus);

    void subscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, WynkService service, TransactionStatus transactionStatus, TransactionEvent transactionEvent);

    void unSubscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, WynkService service, TransactionStatus transactionStatus);

    List<PlanDTO> getPlans();


}
