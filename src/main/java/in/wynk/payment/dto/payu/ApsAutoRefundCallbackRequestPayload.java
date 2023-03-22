package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */

@Getter
@SuperBuilder
@NoArgsConstructor
public class ApsAutoRefundCallbackRequestPayload extends ApsCallBackRequestPayload {
    @JsonProperty("bank_arn")
    private String bankArn;
    @JsonProperty("bank_ref_num")
    private String bankReference;
    @JsonProperty("request_id")
    private String requestId;
    private String token;
    private String action;
    @JsonProperty("amt")
    private String amount;
    private String key;
    private String remark;
    private String additionalValue1;
    private String additionalValue2;
    @JsonProperty("refund_mode")
    private String refundMode;
}
