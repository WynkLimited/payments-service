package in.wynk.payment.dto.request;

import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.subscription.common.request.TriggerDataRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import static in.wynk.common.constant.BaseConstants.UNKNOWN;

import java.util.Optional;

@Getter
@SuperBuilder
public abstract class AbstractUnSubscribePlanRequest implements IObjectMapper {

    private int planId;

    private String uid;
    private String msisdn;
    private String transactionId;

    private PaymentEvent paymentEvent;
    private TransactionStatus transactionStatus;

    private TriggerDataRequest triggerDataRequest;

    public static AbstractUnSubscribePlanRequest from(AbstractTransactionRevisionRequest request) {
        if (SyncTransactionRevisionRequest.class.isAssignableFrom(request.getClass())) {
            return UnSubscribePlanSyncRequest.from((SyncTransactionRevisionRequest) request);
        } else {
            return UnSubscribePlanAsyncRequest.from((AsyncTransactionRevisionRequest) request);
        }
    }

    public static TriggerDataRequest getTriggerData() {
        final Optional<IPurchaseDetails> purchaseDetails = TransactionContext.getPurchaseDetails();
        return TriggerDataRequest.builder().os(purchaseDetails.map(IPurchaseDetails::getAppDetails).map(IAppDetails::getOs).orElse(UNKNOWN))
                .appVersion(purchaseDetails.map(IPurchaseDetails::getAppDetails).map(IAppDetails::getAppVersion).orElse(UNKNOWN))
                .build();
    }

}
