package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class AmazonNotificationMessage {

    @JsonProperty("appPackageName")
    private String appPackageName;
    @JsonProperty("notificationType")
    private String notificationType;
    @JsonProperty("appUserId")
    private String appUserId;
    @JsonProperty("receiptId")
    private String receiptId;
    @JsonProperty("relatedReceipts")
    private Map<String, String> relatedReceipts;
    @JsonProperty("timestamp")
    private int timestamp;
    @JsonProperty("betaProductTransaction")
    private boolean betaProductTransaction;

}

