package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.IAPNotification;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
public class ItunesCallbackRequest implements IAPNotification {

    @JsonProperty("unified_receipt")
    private UnifiedReceipt unifiedReceipt;

    @JsonProperty("notification_type")
    @Analysed
    private String notificationType;

    @JsonProperty("environment")
    @Analysed
    private String environment;

    @JsonProperty("auto_renew_product_id")
    @Analysed
    private String autoRenewProductId;

    @JsonProperty("auto_renew_status")
    @Analysed
    private String autoRenewStatus;

    private String password;
}
