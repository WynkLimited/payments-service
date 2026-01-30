package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.UNKNOWN;

@SuperBuilder
public class UnSubscribePlanSyncRequest extends AbstractUnSubscribePlanRequest {

    public static UnSubscribePlanSyncRequest from(SyncTransactionRevisionRequest request) {
        final Transaction transaction = request.getTransaction();
        return UnSubscribePlanSyncRequest.builder().uid(transaction.getUid()).msisdn(transaction.getMsisdn()).planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).transactionStatus(transaction.getStatus()).triggerDataRequest(getTriggerData()).build();
    }

}
