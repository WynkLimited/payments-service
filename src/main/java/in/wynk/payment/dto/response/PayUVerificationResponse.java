package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.TransactionDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUVerificationResponse {
  private long status;

  @JsonProperty("msg")
  private long message;

  @JsonProperty("transaction_details")
  private Map<String, TransactionDetails> transactionDetails;
}
