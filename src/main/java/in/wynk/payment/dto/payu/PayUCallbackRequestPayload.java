package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.*;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "action", defaultImpl = PayUCallbackRequestPayload.class, visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = PayUAutoRefundCallbackRequestPayload.class, name = PayUConstants.REFUND_CALLBACK_ACTION),  @JsonSubTypes.Type(value = PayuRealtimeMandate.class, name = PayUConstants.REALTIME_MANDATE_CALLBACK_ACTION)})
public class PayUCallbackRequestPayload extends CallbackRequest implements Serializable {

    private String mode;
    private String udf1;
    @Builder.Default
    private String udf2 = StringUtils.EMPTY;
    @Builder.Default
    private String udf3 = StringUtils.EMPTY;
    @Builder.Default
    private String udf4 = StringUtils.EMPTY;
    @Builder.Default
    private String udf5 = StringUtils.EMPTY;
    private String email;
    private String status;
    private String cardToken;
    @JsonProperty("bank_ref_num")
    private String bankReference;
    private double amount;

    @JsonProperty("Error")
    private String error;

    @JsonProperty("bankcode")
    private String bankCode;

    @JsonProperty("firstname")
    private String firstName;

    @JsonProperty("cardnum")
    private String cardNumber;

    @JsonProperty("hash")
    private String responseHash;

    @JsonProperty("field7")
    private String mostSpecificFailureReason;

    @JsonProperty("field8")
    private String specificFailureReason;

    @JsonProperty("field9")
    private String failureReason;

    @JsonProperty("error_Message")
    private String errorMessage;

    @JsonAlias("merchantTxnId")
    @JsonProperty(value = "txnid")
    private String transactionId;

    @JsonProperty(value = "payment_source")
    private String paymentSource;

    @JsonProperty(value = "unmappedstatus")
    private String unmappedStatus;

    @JsonProperty("mihpayid")
    private String externalTransactionId;

    @JsonProperty("productinfo")
    private String productInfo;

    public String getTransactionFailureReason() {
        final StringBuilder reason = new StringBuilder();
        if (StringUtils.isNotEmpty(mostSpecificFailureReason))
            reason.append(mostSpecificFailureReason).append(PaymentConstants.PIPE_SEPARATOR);
        if (StringUtils.isNotEmpty(specificFailureReason))
            reason.append(specificFailureReason).append(PaymentConstants.PIPE_SEPARATOR);
        if (StringUtils.isNotEmpty(failureReason))
            reason.append(failureReason).append(PaymentConstants.PIPE_SEPARATOR);
        if (StringUtils.isNotEmpty(errorMessage))
            reason.append(errorMessage);
        return reason.toString();
    }

    public String getAction() {
        return PayUConstants.GENERIC_CALLBACK_ACTION;
    }

    public String getUdf() {
        return  getUDFOrDefault(udf5) +
                getUDFOrDefault(udf4) +
                getUDFOrDefault(udf3) +
                getUDFOrDefault(udf2) +
                udf1;
    }

    private String getUDFOrDefault(String value) {
        return StringUtils.isEmpty(value) ? PaymentConstants.PIPE_SEPARATOR : value.concat(PaymentConstants.PIPE_SEPARATOR);
    }

}