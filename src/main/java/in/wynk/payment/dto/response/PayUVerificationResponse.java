package in.wynk.payment.dto.response;

import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.TransactionDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PayUVerificationResponse extends ChargingStatus {
  private long status;

  @SerializedName("msg")
  private String message;

  @SerializedName("transaction_details")
  private Map<String, TransactionDetails> transactionDetails;
}
