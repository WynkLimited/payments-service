package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.ToString;

/**
 * An object that contains information about the most-recent, in-app purchase transactions for the app.
 */

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@ToString
public class UnifiedReceipt {
    @JsonProperty("latest_receipt_info")
    private LatestReceiptInfo latestReceiptInfo;

    @JsonProperty("pending_renewal_info")
    private PendingRenewalInfo pendingRenewalInfo;
}
