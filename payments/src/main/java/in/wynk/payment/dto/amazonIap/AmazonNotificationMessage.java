package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Getter;

import java.util.Map;

@Getter
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmazonNotificationMessage {

    @Analysed
    @JsonProperty("appPackageName")
    private String appPackageName;
    @Analysed
    @JsonProperty("notificationType")
    private String notificationType;
    @Analysed
    @JsonProperty("appUserId")
    private String appUserId;
    @JsonProperty("receiptId")
    @Analysed
    private String receiptId;
    @JsonProperty("relatedReceipts")
    private Map<String, String> relatedReceipts;
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("betaProductTransaction")
    private boolean betaProductTransaction;

    @Analysed(name = "environment")
    public String getEnvironment() {
        return betaProductTransaction ? PaymentConstants.SANDBOX_ENV : PaymentConstants.PROD_ENV;
    }

}

