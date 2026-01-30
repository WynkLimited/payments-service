package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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

    @SerializedName("field7")
    private String mostSpecificFailureReason;

    @SerializedName("field8")
    private String specificFailureReason;

    @JsonProperty("field9")
    private String payUResponseFailureMessage;

    @JsonProperty("payment_source")
    private String paymentSource;

    @JsonProperty("bankcode")
    private String bankCode;

    @JsonProperty("card_type")
    private String cardType;

    @JsonProperty("unmappedstatus")
    private String unMappedStatus;

    @JsonProperty("authpayuid")
    private String authpayuId;

    @JsonProperty("phone")
    private String msisdn;

    @JsonProperty("email")
    private String email;

    public String getBankCode() {
        if (StringUtils.isNotEmpty(bankCode)) return bankCode.replace(PayUConstants.PAYU_SI, StringUtils.EMPTY);
        return bankCode;
    }

    @Setter
    private String migratedTransactionId;

    public String getTransactionFailureReason() {
        final StringBuilder reason = new StringBuilder();
        if (StringUtils.isNotEmpty(mostSpecificFailureReason))
            reason.append(mostSpecificFailureReason).append(PaymentConstants.PIPE_SEPARATOR);
        if (StringUtils.isNotEmpty(specificFailureReason))
            reason.append(specificFailureReason).append(PaymentConstants.PIPE_SEPARATOR);
        if (StringUtils.isNotEmpty(payUResponseFailureMessage))
            reason.append(payUResponseFailureMessage).append(PaymentConstants.PIPE_SEPARATOR);
        if (StringUtils.isNotEmpty(errorMessage))
            reason.append(errorMessage);
        return reason.toString();
    }

    public static PayUChargingTransactionDetails from(PayUCallbackRequestPayload payload) {
        return PayUChargingTransactionDetails.builder()
                .mode(payload.getMode())
                .status(payload.getStatus())
                .amount(payload.getAmount())
                .payUUdf1(payload.getUdf1())
                .errorCode(payload.getError())
                .bankCode(payload.getBankCode())
                .errorMessage(payload.getErrorMessage())
                .transactionId(payload.getTransactionId())
                .paymentSource(payload.getPaymentSource())
                .payuId(payload.getExternalTransactionId())
                .responseCardNumber(payload.getCardNumber())
                .unMappedStatus(payload.getUnmappedStatus())
                .bankReferenceNum(payload.getBankReference())
                .payUExternalTxnId(payload.getExternalTransactionId())
                .payUResponseFailureMessage(payload.getFailureReason())
                .specificFailureReason(payload.getSpecificFailureReason())
                .mostSpecificFailureReason(payload.getMostSpecificFailureReason())
                .build();
    }

}
