package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.stream.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class PurchaseAcknowledgementMessageThresholdEvent extends MessageThresholdExceedEvent {
    @Analysed
    private String packageName;

    @Analysed
    private String service;

    @Analysed
    private String purchaseToken;

    @Analysed
    private String developerPayload;

    @Analysed
    private String skuId;

    @NotNull
    @Analysed
    private String paymentCode;

    @Analysed
    private String txnId;
}
