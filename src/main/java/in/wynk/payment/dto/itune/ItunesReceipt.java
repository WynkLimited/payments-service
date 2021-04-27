package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesReceipt {

    @JsonProperty("latest_receipt_info")
    List<LatestReceiptInfo> latestReceiptInfoList;

    String status;

    @JsonProperty("latest_receipt")
    String latestReceipt;

    @JsonProperty("pending_renewal_info")
    List<PendingRenewalInfo> pendingRenewalInfo;

    String environment;

    public List<PendingRenewalInfo> getPendingRenewalInfo() {
        return CollectionUtils.isNotEmpty(pendingRenewalInfo) ? pendingRenewalInfo : Collections.EMPTY_LIST;
    }

}

