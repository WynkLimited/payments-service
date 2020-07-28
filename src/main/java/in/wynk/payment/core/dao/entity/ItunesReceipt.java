package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.core.dao.entity.LatestReceiptInfo;
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

