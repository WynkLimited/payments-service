package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesReceipt {

    @JsonProperty("latest_receipt_info")
    List<LatestReceiptInfo> latestReceiptInfoList;

    String status;

    @JsonProperty("latest_receipt")
    String latestReceipt;

    String environment;


}

