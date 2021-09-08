package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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

    @JsonProperty("bankcode")
    private String bankCode;

    @JsonProperty("card_type")
    private String cardType;

    public String getBankCode() {
        if (StringUtils.isNotEmpty(bankCode)) return bankCode.replace(PayUConstants.PAYU_SI, StringUtils.EMPTY);
        return bankCode;
    }

    @Setter
    private String migratedTransactionId;

}
