package in.wynk.payment.dto.payu;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class PayUAutoRefundCallbackRequestPayload extends PayUCallbackRequestPayload {

    @SerializedName("bank_arn")
    private String bankArn;
    @SerializedName("bank_ref_num")
    private String bankReference;



}
