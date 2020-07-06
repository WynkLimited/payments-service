package in.wynk.payment.dto.response.payu;

import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.payu.PayUTransactionDetails;
import in.wynk.payment.dto.response.ChargingStatus;
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
  private Map<String, PayUTransactionDetails> transactionDetails;
}
