package in.wynk.payment.dto.response.payu;

import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import lombok.*;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayURenewalResponse {

  private long status;

  @SerializedName("message")
  private String message;

  @SerializedName("details")
  private Map<String, PayUChargingTransactionDetails> transactionDetails;

}
