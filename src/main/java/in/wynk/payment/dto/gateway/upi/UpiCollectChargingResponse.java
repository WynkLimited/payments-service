package in.wynk.payment.dto.gateway.upi;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "COLLECT")
public class UpiCollectChargingResponse extends AbstractNonSeamlessUpiChargingResponse {
    @JsonProperty("redirectUrl")
    private String url;
}
