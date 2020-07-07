package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.revenue.commons.TransactionEvent;
import in.wynk.revenue.commons.TransactionStatus;

public interface ISubscriptionServiceManager {

     PlanDTO getPlan(int planId);

     String publish(int planId, String uid, String transactionId, TransactionStatus transactionStatus, TransactionEvent transactionEvent);
}
