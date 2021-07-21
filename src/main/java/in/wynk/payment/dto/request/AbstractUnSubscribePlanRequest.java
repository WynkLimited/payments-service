package in.wynk.payment.dto.request;

import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractUnSubscribePlanRequest implements IObjectMapper {

    private int planId;

    private String uid;
    private String msisdn;
    private String transactionId;

    private PaymentEvent paymentEvent;
    private TransactionStatus transactionStatus;

    public static AbstractUnSubscribePlanRequest from(AbstractTransactionRevisionRequest request) {
        if (SyncTransactionRevisionRequest.class.isAssignableFrom(request.getClass())) {
            return UnSubscribePlanSyncRequest.from((SyncTransactionRevisionRequest) request);
        } else {
            return UnSubscribePlanAsyncRequest.from((AsyncTransactionRevisionRequest) request);
        }
    }

}
