package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayUCallbackRequestPayload {
  @JsonProperty("bankcode")
  private String bankCode;

  private String mode;
  private String status;

  @JsonProperty("mihpayid")
  private String externalTransactionId;

  @JsonProperty("Error")
  private String error;

  @JsonProperty("error_Message")
  private String errorMessage;

  private String udf1;
  private String cardToken;

  @JsonProperty("card_no")
  private String cardNumber;

  private String email;

  @JsonProperty("firstname")
  private String firstName;

  @JsonProperty("hash")
  private String responseHash;
}
