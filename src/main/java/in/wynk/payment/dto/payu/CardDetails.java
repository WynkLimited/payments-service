package in.wynk.payment.dto.payu;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardDetails {
  @SerializedName("card_type")
  private String cardType;

  @SerializedName("expiry_year")
  private String expiryYear;

  @SerializedName("is_domestic")
  private String isDomestic;

  @SerializedName("expiry_month")
  private String expiryMonth;

  @SerializedName("is_expired")
  private String isExpired;

  @SerializedName("card_mode")
  private String cardMode;

  @SerializedName("card_cvv")
  private String cardCVV;

  @SerializedName("card_no")
  private String cardNo;

  @SerializedName("card_token")
  private String cardToken;

  @SerializedName("card_name")
  private String cardName;

  @SerializedName("card_brand")
  private String cardBrand;

  @SerializedName("name_on_card")
  private String nameOnCard;

  @SerializedName("card_bin")
  private String cardBin;

  private String issuingBank;
}
