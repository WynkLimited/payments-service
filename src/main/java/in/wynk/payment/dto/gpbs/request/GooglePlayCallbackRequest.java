package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.IAPNotification;
import in.wynk.payment.dto.gpbs.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@Builder
public class GooglePlayCallbackRequest implements IAPNotification {
    private String packageName;
    private int notificationType;
    private String purchaseToken;
    private String subscriptionId;
    private GooglePlayLatestReceiptResponse googlePlayLatestReceiptResponse;

    @Override
    public String getNotificationType () {
        return String.valueOf(this.notificationType);
    }
}
