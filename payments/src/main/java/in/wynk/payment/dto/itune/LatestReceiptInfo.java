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
public class LatestReceiptInfo {

    @Analysed
    @JsonProperty("cancellation_date")
    private String cancellationDate;

    @JsonProperty("cancellation_date_ms")
    @Analysed
    private String cancellationDateMs;

    @Analysed
    @JsonProperty("cancellation_date_pst")
    private String cancellationDatePst;

    @JsonProperty("expires_date_ms")
    @Analysed
    private String expiresDateMs;

    @JsonProperty("expires_date")
    private String expiresDate;

    @JsonProperty("cancellation_reason")
    @Analysed
    private String cancellationReason;

    @JsonProperty("expires_date_pst")
    private String expiresDatePst;

    @JsonProperty("is_in_intro_offer_period")
    @Analysed
    private String isInIntroOfferPeriod;

    @JsonProperty("is_trial_period")
    @Analysed
    private String isTrialPeriod;

    @Analysed
    @JsonProperty("is_upgraded")
    private String isUpgraded;

    @Analysed
    @JsonProperty("original_purchase_date")
    private String originalPurchaseDate;

    @Analysed
    @JsonProperty("original_purchase_date_ms")
    private String originalPurchaseDateMs;

    @JsonProperty("original_purchase_date_pst")
    private String originalPurchaseDatePst;

    @JsonProperty("original_transaction_id")
    @Analysed
    private String originalTransactionId;

    @JsonProperty("product_id")
    @Analysed
    private String productId;

    @JsonProperty("purchase_date")
    private String purchaseDate;

    @JsonProperty("purchase_date_ms")
    private String purchaseDateMs;

    @JsonProperty("purchase_date_pst")
    private String purchaseDatePst;

    private String quantity;

    @Analysed
    @JsonProperty("subscription_group_identifier")
    private String subscriptionGroupIdentifier;

    @JsonProperty("transaction_id")
    @Analysed
    private String transactionId;

    @JsonProperty("web_order_line_item_id")
    private String webOrderLineItemId;

    @JsonProperty("offer_code_ref_name")
    private String offerCodeRefName;
}
