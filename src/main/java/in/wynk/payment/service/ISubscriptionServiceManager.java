package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;

public interface ISubscriptionServiceManager {

     PlanDTO getPlan(int planId);

     String publish(int planId, String uid, String transactionId, TransactionStatus transactionStatus, TransactionEvent transactionEvent);
}
