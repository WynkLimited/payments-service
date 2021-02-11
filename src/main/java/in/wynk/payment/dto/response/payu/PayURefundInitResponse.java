package in.wynk.payment.dto.response.payu;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PayURefundInitResponse {

    @SerializedName("status")
    private long status;
    @SerializedName("msg")
    private String message;
    @SerializedName("request_id")
    private String requestId;
    @SerializedName("txn_update_id")
    private String txnUpdateId;
    @SerializedName("bank_ref_num")
    private String bankReferenceNumber;
    @SerializedName("mihpayid")
    private String authPayUId;

}
