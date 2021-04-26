package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * An object that contains information about the most-recent, in-app purchase transactions for the app.
 */

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@ToString
public class UnifiedReceipt {
    @Analysed
    @JsonProperty("latest_receipt_info")
    private List<LatestReceiptInfo> latestReceiptInfoList;

    @Analysed
    @JsonProperty("pending_renewal_info")
    private List<PendingRenewalInfo> pendingRenewalInfoList;

    @JsonProperty("latest_receipt")
    private String latestReceipt;

    @JsonProperty("environment")
    private String environment;

    @JsonProperty("status")
    private Integer status;

    public List<PendingRenewalInfo> getPendingRenewalInfoList() {
        return CollectionUtils.isNotEmpty(pendingRenewalInfoList) ? pendingRenewalInfoList : Collections.EMPTY_LIST;
    }
}
