package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class UnSubscribePlanAsyncRequest extends AbstractUnSubscribePlanRequest {

    public static UnSubscribePlanAsyncRequest from(AsyncTransactionRevisionRequest request) {
        final Transaction transaction = request.getTransaction();
        return UnSubscribePlanAsyncRequest.builder().uid(transaction.getUid()).msisdn(transaction.getMsisdn()).planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).transactionStatus(transaction.getStatus()).triggerDataRequest(getTriggerData()).build();
    }

}