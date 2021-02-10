package in.wynk.payment.dto.response.payu;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class PayURefundTransactionDetails {

    @SerializedName("amt")
    private long amount;

    @SerializedName("request_id")
    private int requestId;

    @SerializedName("bank_ref_num")
    private String bankRefNum;

    @SerializedName("mode")
    private String mode;

    @SerializedName("status")
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
