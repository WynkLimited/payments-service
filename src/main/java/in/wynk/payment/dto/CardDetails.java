package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardDetails {
  @JsonProperty("card_type")
  private String cardType;

  @JsonProperty("expiry_year")
  private String expiryYear;

  @JsonProperty("is_domestic")
  private String isDomestic;

  @JsonProperty("expiry_month")
  private String expiryMonth;

  @JsonProperty("is_expired")
  private String isExpired;

  @JsonProperty("card_mode")
  private String cardMode;

  @JsonProperty("card_cvv")
  private String cardCVV;

  @JsonProperty("card_no")
  private String cardNo;

  @JsonProperty("card_token")
  private String cardToken;

  @JsonProperty("card_name")
  private String cardName;

  @JsonProperty("card_brand")
  private String cardBrand;

  @JsonProperty("name_on_card")
  private String nameOnCard;

  @JsonProperty("card_bin")
  private String cardBin;

  private String issuingBank;
}
