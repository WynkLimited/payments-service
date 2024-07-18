package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PayUAutoRefundCallbackRequestPayload extends PayUCallbackRequestPayload {

    @JsonProperty("bank_arn")
    private String bankArn;
    @JsonProperty("request_id")
    private String requestId;
    private String token;
    private String action;
    @JsonProperty("amt")
    private double amount;
    private String key;
    private String remark;
    private String additionalValue1;
    private String additionalValue2;
    @JsonProperty("refund_mode")
    private String refundMode;

    public String getFlow() {
        return PayUConstants.REFUND_CALLBACK_ACTION;
    }
}
