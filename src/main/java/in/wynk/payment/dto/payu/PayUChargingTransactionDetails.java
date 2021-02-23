package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PayUChargingTransactionDetails extends AbstractPayUTransactionDetails {

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

  @JsonProperty("payuid")
  private String payuId;

  @JsonProperty("transactionid")
  private String transactionId;

  @JsonProperty("field9")
  private String payUResponseFailureMessage;

  @Setter
  private String migratedTransactionId;

}
