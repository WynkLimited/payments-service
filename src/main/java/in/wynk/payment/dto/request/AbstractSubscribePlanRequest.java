package in.wynk.payment.dto.request;

import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class AbstractSubscribePlanRequest implements IObjectMapper {

    private final int planId;

    private final String uid;
    private final String msisdn;
    private final String paymentCode;
    private final String transactionId;

    private final PaymentEvent paymentEvent;
    private final TransactionStatus transactionStatus;

    public static AbstractSubscribePlanRequest from(AbstractTransactionRevisionRequest request) {
        if(SyncTransactionRevisionRequest.class.isAssignableFrom(request.getClass())) {
            return SubscribePlanSyncRequest.from((SyncTransactionRevisionRequest) request);
        } else {
            return SubscribePlanAsyncRequest.from((AsyncTransactionRevisionRequest) request);
        }
    }

}
