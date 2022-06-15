package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "action", defaultImpl = PayUCallbackRequestPayload.class, visible = true)
@JsonSubTypes(@JsonSubTypes.Type(value = PayUAutoRefundCallbackRequestPayload.class, name = "refund"))
public class PayUCallbackRequestPayload extends CallbackRequest implements Serializable {

    private String mode;
    private String udf1;
    private String email;
    private String status;
    private String cardToken;

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

    @JsonProperty("mihpayid")
    private String externalTransactionId;

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
        return PayUConstants.GENERIC_CALLBACK;
    }

}