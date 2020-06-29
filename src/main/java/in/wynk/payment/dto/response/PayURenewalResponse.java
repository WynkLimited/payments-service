package in.wynk.payment.dto.response;

import in.wynk.payment.dto.TransactionDetails;
import lombok.*;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayURenewalResponse {
  private Map<String, TransactionDetails> details;
  @Setter
  private boolean timeOutFlag;
}
