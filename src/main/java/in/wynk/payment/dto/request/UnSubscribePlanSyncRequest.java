package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class UnSubscribePlanSyncRequest extends AbstractUnSubscribePlanRequest {

    public static UnSubscribePlanSyncRequest from(SyncTransactionRevisionRequest request) {
        final Transaction transaction = request.getTransaction();
        return UnSubscribePlanSyncRequest.builder().uid(transaction.getUid()).msisdn(transaction.getMsisdn()).planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).transactionStatus(transaction.getStatus()).build();
    }

}
