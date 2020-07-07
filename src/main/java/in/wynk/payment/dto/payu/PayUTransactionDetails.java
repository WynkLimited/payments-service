package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayUTransactionDetails {
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

}
