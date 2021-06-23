package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SubscribePlanAsyncRequest extends AbstractSubscribePlanRequest {

    public static SubscribePlanAsyncRequest from(AsyncTransactionRevisionRequest request) {
        final Transaction transaction = request.getTransaction();
        return SubscribePlanAsyncRequest.builder().uid(transaction.getUid()).msisdn(transaction.getMsisdn()).planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).transactionStatus(transaction.getStatus()).paymentCode(transaction.getPaymentChannel().getCode()).build();
    }

}