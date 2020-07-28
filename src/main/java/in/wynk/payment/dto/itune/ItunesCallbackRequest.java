package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.core.dao.entity.LatestReceiptInfo;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesCallbackRequest {

    @JsonProperty("latest_receipt_info")
    private LatestReceiptInfo latestReceiptInfo;

    @JsonProperty("latest_receipt")
    private String latestReceipt;
}
