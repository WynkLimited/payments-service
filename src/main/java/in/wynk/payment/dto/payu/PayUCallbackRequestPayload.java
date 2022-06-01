package in.wynk.payment.dto.payu;

import com.google.gson.annotations.SerializedName;
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
public class PayUCallbackRequestPayload extends CallbackRequest implements Serializable {

    private String mode;
    private String udf1;
    private String email;
    private String status;
    private String cardToken;

    @SerializedName("Error")
    private String error;

    @SerializedName("bankcode")
    private String bankCode;

    @SerializedName("firstname")
    private String firstName;

    @SerializedName("cardnum")
    private String cardNumber;

    @SerializedName("hash")
    private String responseHash;

    @SerializedName("field7")
    private String mostSpecificFailureReason;

    @SerializedName("field8")
    private String specificFailureReason;

    @SerializedName("field9")
    private String failureReason;

    @SerializedName("error_Message")
    private String errorMessage;

    @SerializedName("txnid")
    private String transactionId;

    @SerializedName("mihpayid")
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

}