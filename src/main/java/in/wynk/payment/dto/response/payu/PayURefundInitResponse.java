package in.wynk.payment.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class PayURefundInitResponse {

    @JsonProperty("status")
    private long status;
    @JsonProperty("msg")
    private String message;
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("txn_update_id")
    private String txnUpdateId;
    @JsonProperty("bank_ref_num")
    private String bankReferenceNumber;
    @JsonProperty("mihpayid")
    private String authPayUId;

}
