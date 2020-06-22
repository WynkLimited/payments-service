package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.CardDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayUUserCardDetailsResponse {
  @JsonProperty("msg")
  private String message;

  @JsonProperty("user_cards")
  private Map<String, CardDetails> userCards;

  private String status;
}
