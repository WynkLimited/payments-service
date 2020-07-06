package in.wynk.payment.dto.response.payu;

import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.payu.CardDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayUUserCardDetailsResponse {
  @SerializedName("msg")
  private String message;

  @SerializedName("user_cards")
  private Map<String, CardDetails> userCards;

  private String status;

  public Map<String, CardDetails> getUserCards(){
    if (userCards == null)
      userCards = new HashMap<>();
    return userCards;
  }
}
