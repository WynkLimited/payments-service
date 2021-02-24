package in.wynk.payment.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import lombok.*;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayURenewalResponse {

  private long status;

  @JsonProperty("msg")
  @JsonAlias("message")
  private String message;

  @JsonAlias("transactionDetails")
  @JsonProperty("transaction_details")
  private Map<String, PayUChargingTransactionDetails> transactionDetails;

}
