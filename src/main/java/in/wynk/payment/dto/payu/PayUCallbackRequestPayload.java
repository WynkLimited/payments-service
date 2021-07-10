package in.wynk.payment.dto.payu;

import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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

    @SerializedName("error_Message")
    private String errorMessage;

    @SerializedName("txnid")
    private String transactionId;

    @SerializedName("mihpayid")
    private String externalTransactionId;

}