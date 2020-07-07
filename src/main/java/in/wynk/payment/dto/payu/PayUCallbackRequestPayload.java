package in.wynk.payment.dto.payu;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayUCallbackRequestPayload implements Serializable {
  @SerializedName("bankcode")
  private String bankCode;

  private String mode;
  private String status;

  @SerializedName("mihpayid")
  private String externalTransactionId;

  @SerializedName("Error")
  private String error;

  @SerializedName("error_Message")
  private String errorMessage;

  private String udf1;
  private String cardToken;

  @SerializedName("cardnum")
  private String cardNumber;

  private String email;

  @SerializedName("firstname")
  private String firstName;

  @SerializedName("hash")
  private String responseHash;
}
