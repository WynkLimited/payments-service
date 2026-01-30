package in.wynk.payment.dto.request;

import static in.wynk.common.constant.BaseConstants.UNKNOWN;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.subscription.common.request.TriggerDataRequest;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@Getter
@SuperBuilder
@ToString
public class AbstractSubscribePlanRequest implements IObjectMapper {

    private final int planId;

    private final String uid;
    private final String msisdn;
    private final String subscriberId;
    private final String transactionId;

    private final PaymentGateway paymentGateway;
    private final PaymentEvent paymentEvent;
    private final TransactionStatus transactionStatus;

    private TriggerDataRequest triggerDataRequest;

    public static AbstractSubscribePlanRequest from(AbstractTransactionRevisionRequest request) {
        if(SyncTransactionRevisionRequest.class.isAssignableFrom(request.getClass())) {
            return SubscribePlanSyncRequest.from((SyncTransactionRevisionRequest) request);
        } else {
            return SubscribePlanAsyncRequest.from((AsyncTransactionRevisionRequest) request);
        }
    }

    public static TriggerDataRequest getTriggerData() {
        final Optional<IPurchaseDetails> purchaseDetails = TransactionContext.getPurchaseDetails();
        return TriggerDataRequest.builder().os(purchaseDetails.map(IPurchaseDetails::getAppDetails).map(IAppDetails::getOs).orElse(UNKNOWN))
                .appVersion(purchaseDetails.map(IPurchaseDetails::getAppDetails).map(IAppDetails::getAppVersion).orElse(UNKNOWN))
                .build();
    }

}