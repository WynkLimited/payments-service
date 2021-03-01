package in.wynk.payment.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayURenewalResponse {

  private long status;

  @JsonProperty("msg")
  @JsonAlias("message")
  private String message;

  @JsonAlias({"transactionDetails", "details"})
  @JsonProperty("transaction_details")
  private Map<String, PayUChargingTransactionDetails> transactionDetails;

}
