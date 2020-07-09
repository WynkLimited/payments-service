package in.wynk.payment.service;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;

import java.util.List;

public interface ISubscriptionServiceManager {

     String publish(int planId, String uid, String transactionId, TransactionStatus transactionStatus, TransactionEvent transactionEvent);

    List<PlanDTO> getPlans();
}
