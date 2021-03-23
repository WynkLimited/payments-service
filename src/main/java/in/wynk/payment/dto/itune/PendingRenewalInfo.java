package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.ToString;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@ToString
public class PendingRenewalInfo {

    @JsonProperty("auto_renew_product_id")
    @Analysed
    private String autoRenewProductId;

    @JsonProperty("auto_renew_status")
    @Analysed
    private String autoRenewStatus;

    @JsonProperty("expiration_intent")
    @Analysed
    private String expirationIntent;

    @JsonProperty("grace_period_expires_date")
    private String gracePeriodExpiresDate;

    @JsonProperty("grace_period_expires_date_ms")
    @Analysed
    private String gracePeriodExpiresDateMs;

    @JsonProperty("grace_period_expires_date_pst")
    private String gracePeriodExpiresDatePst;

    @JsonProperty("is_in_billing_retry_period")
    @Analysed
    private String inBillingRetryPeriod;

    @JsonProperty("offer_code_ref_name")
    private String offerCodeRefName;

    @JsonProperty("original_transaction_id")
    @Analysed
    private String originalTransactionId;

    @JsonProperty("price_consent_status")
    private String priceConsentStatus;

    @JsonProperty("product_id")
    @Analysed
    private String productId;

    @JsonProperty("promotional_offer_id")
    private String promotionalOfferId;

}
