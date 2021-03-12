package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesCallbackRequest {

    @JsonProperty("unified_receipt")
    private UnifiedReceipt unifiedReceipt;

    @JsonProperty("latest_receipt")
    private String latestReceipt;

    @JsonProperty("notification_type")
    private String notificationType;

}
