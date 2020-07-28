package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatestReceiptInfo {

    @JsonProperty("cancellation_date")
    String cancellationDate;

    @JsonProperty("cancellation_date_ms")
    String cancellationDateMs;

    @JsonProperty("cancellation_date_pst")
    String cancellationDatePst;

    @JsonProperty("expires_date_ms")
    String expiresDateMs;

    @JsonProperty("expires_date")
    String expiresDate;

    @JsonProperty("cancellation_reason")
    String cancellationReason;

    @JsonProperty("expires_date_pst")
    String expiresDatePst;

    @JsonProperty("is_in_intro_offer_period")
    String isInIntroOfferPeriod;

    @JsonProperty("is_trial_period")
    String isTrialPeriod;

    @JsonProperty("is_upgraded")
    String isUpgraded;

    @JsonProperty("original_purchase_date")
    String originalPurchaseDate;

    @JsonProperty("original_purchase_date_ms")
    String originalPurchaseDateMs;

    @JsonProperty("original_purchase_date_pst")
    String originalPurchaseDatePst;

    @JsonProperty("original_transaction_id")
    String originalTransactionId;

    @JsonProperty("product_id")
    String productId;

    @JsonProperty("purchase_date")
    String purchaseDate;

    @JsonProperty("purchase_date_ms")
    String purchaseDateMs;

    @JsonProperty("purchase_date_pst")
    String purchaseDatePst;

    String quantity;

    @JsonProperty("subscription_group_identifier")
    String subscriptionGroupIdentifier;

    @JsonProperty("transaction_id")
    String transactionId;

    @JsonProperty("web_order_line_item_id")
    String webOrderLineItemId;

}
