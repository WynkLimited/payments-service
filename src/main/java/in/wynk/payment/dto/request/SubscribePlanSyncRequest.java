package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SubscribePlanSyncRequest extends AbstractSubscribePlanRequest {

    public static SubscribePlanSyncRequest from(SyncTransactionRevisionRequest request) {
        final Transaction transaction = request.getTransaction();
        SubscribePlanSyncRequest.SubscribePlanSyncRequestBuilder<?,?> builder = SubscribePlanSyncRequest.builder();
        TransactionContext.getPurchaseDetails().ifPresent(purchaseDetails -> builder.subscriberId(purchaseDetails.getUserDetails().getSubscriberId()));
        return builder.uid(transaction.getUid()).msisdn(transaction.getMsisdn()).planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).transactionStatus(transaction.getStatus()).paymentCode(transaction.getPaymentChannel().getCode()).build();
    }

}