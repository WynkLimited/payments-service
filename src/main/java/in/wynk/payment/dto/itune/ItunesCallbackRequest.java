package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesCallbackRequest {

    @JsonProperty("unified_receipt")
    private UnifiedReceipt unifiedReceipt;

    @JsonProperty("notification_type")
    private String notificationType;

    @JsonProperty("environment")
    private String environment;

    @JsonProperty("auto_renew_product_id")
    @Analysed
    private String autoRenewProductId;

    @JsonProperty("auto_renew_status")
    @Analysed
    private String autoRenewStatus;

}
