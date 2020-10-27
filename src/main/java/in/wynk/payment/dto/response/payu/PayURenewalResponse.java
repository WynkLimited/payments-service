package in.wynk.payment.dto.response.payu;

import in.wynk.payment.dto.payu.PayUTransactionDetails;
import lombok.*;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayURenewalResponse {
  private Map<String, PayUTransactionDetails> details;
}
