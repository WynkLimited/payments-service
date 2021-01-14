package in.wynk.payment.dto.payu;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayUTransactionDetails {

  @SerializedName("mode")
  private String mode;

  @SerializedName("status")
  private String status;

  @SerializedName("mihpayid")
  private String payUExternalTxnId;

  @SerializedName("addedon")
  private String payUTransactionDate;

  @SerializedName("error_code")
  private String errorCode;

  @SerializedName("error_Message")
  private String errorMessage;

  @SerializedName("udf1")
  private String payUUdf1;

  @SerializedName("card_no")
  private String responseCardNumber;

  @SerializedName("payuid")
  private String payuId;

  @SerializedName("transactionid")
  private String transactionId;

  @SerializedName("field9")
  private String payUResponseFailureMessage;

  private String migratedTransactionId;

}
