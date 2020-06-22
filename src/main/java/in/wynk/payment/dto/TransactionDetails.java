package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDetails {
  private String status;

  @JsonProperty("mihpayid")
  private String payUExternalTxnId;

  @JsonProperty("addedon")
  private String payUTransactionDate;

  @JsonProperty("error_code")
  private String errorCode;

  @JsonProperty("error_Message")
  private String errorMessage;

  @JsonProperty("udf1")
  private String payUUdf1;

  @JsonProperty("card_no")
  private String responseCardNumber;
}
