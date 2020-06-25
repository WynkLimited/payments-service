package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.TransactionDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayUVerificationResponse {
  private long status;

  @SerializedName("msg")
  private long message;

  @SerializedName("transaction_details")
  private Map<String, TransactionDetails> transactionDetails;
}
