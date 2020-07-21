package in.wynk.payment.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.core.dto.itunes.LatestReceiptInfo;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesCallbackRequest {

    @JsonProperty("latest_receipt_info")
    private LatestReceiptInfo latestReceiptInfo;

    @JsonProperty("latest_receipt")
    private String latestReceipt;
}
