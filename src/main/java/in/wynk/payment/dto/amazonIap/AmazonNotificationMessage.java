package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class AmazonNotificationMessage {

    @JsonProperty("appPackageName")
    public String appPackageName;
    @JsonProperty("notificationType")
    public String notificationType;
    @JsonProperty("appUserId")
    public String appUserId;
    @JsonProperty("receiptId")
    public String receiptId;
    @JsonProperty("relatedReceipts")
    public Map<String, String> relatedReceipts;
    @JsonProperty("timestamp")
    public int timestamp;
    @JsonProperty("betaProductTransaction")
    public boolean betaProductTransaction;

}

