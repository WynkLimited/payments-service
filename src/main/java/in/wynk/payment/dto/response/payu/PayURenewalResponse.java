package in.wynk.payment.dto.response.payu;

import in.wynk.payment.dto.payu.PayUTransactionDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayURenewalResponse {
  private Map<String, PayUTransactionDetails> details;
  @Setter
  private boolean timeOutFlag;
}
