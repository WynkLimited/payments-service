package in.wynk.payment.presentation.dto.charge.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Payment(groupId = "UPI", mode = "collect")
public class CollectNonSeamlessUpiPaymentChargingResponse extends NonSeamlessUpiPaymentChargingResponse {
    @JsonProperty("info")
    private String form;
}