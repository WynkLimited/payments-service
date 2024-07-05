package in.wynk.payment.presentation.dto.charge.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Payment(groupId = "UPI", mode = "intent")
public class IntentSeamlessUpiPaymentChargingResponse extends SeamlessUpiPaymentChargingResponse {
    @JsonProperty("package")
    private String appPackage;
    @JsonProperty("info")
    private String deepLink;
}