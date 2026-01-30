package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.event.PurchaseInitEvent;
import in.wynk.scheduler.task.dto.ITaskEntity;
import lombok.*;

@Getter
@Builder
@ToString
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRecord implements ITaskEntity {

    @Analysed
    private String sid;
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String clientAlias;
    @Analysed
    private String transactionId;
    @Analysed
    private AppDetails appDetails;
    @Analysed
    private AbstractProductDetails productDetails;

    @Override
    public String getTaskId() {
        return uid + BaseConstants.DELIMITER + productDetails.getId();
    }

    @Override
    public String getGroupId() {
        return PaymentConstants.USER_WINBACK;
    }

    public static PurchaseRecord from(PurchaseInitEvent purchaseInitEvent) {
        return PurchaseRecord.builder()
                .uid(purchaseInitEvent.getUid())
                .msisdn(purchaseInitEvent.getMsisdn())
                .sid(purchaseInitEvent.getSid().orElse(null))
                .clientAlias(purchaseInitEvent.getClientAlias())
                .transactionId(purchaseInitEvent.getTransactionId())
                .appDetails((AppDetails) purchaseInitEvent.getAppDetails())
                .productDetails((AbstractProductDetails) purchaseInitEvent.getProductDetails())
                .build();
    }

    public PurchaseRecordEvent fromSelf() {
        return new PurchaseRecordEvent(sid, uid, msisdn, clientAlias, transactionId, appDetails, productDetails);
    }

}
