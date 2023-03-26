package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class CardSavedDetails extends AbstractSavedPaymentDTO {
    @JsonProperty("card_token")
    private String cardToken;
    @JsonProperty("card_number")
    private String cardNumber;
    @JsonProperty("bank_name")
    private String bankName;
    @JsonProperty("expiry_month")
    private String expiryMonth;
    @JsonProperty("expiry_year")
    private String expiryYear;
    @JsonProperty("card_type")
    private String cardType;
    @JsonProperty("card_category")
    private String cardCategory;
    @JsonProperty("name_on_card")
    private String nameOnCard;
    @JsonProperty("card_bin")
    private String cardbin;
    @JsonProperty("is_domestic")
    private String domestic;
    @JsonProperty("bank_code")
    private String bankCode;
    @JsonProperty("cvv_length")
    private Integer cvv;
    @JsonProperty("icon_url")
    private Integer icon;
    @JsonProperty("is_active")
    private boolean active;
}
