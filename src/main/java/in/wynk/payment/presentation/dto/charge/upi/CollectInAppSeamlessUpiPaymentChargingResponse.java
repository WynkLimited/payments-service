package in.wynk.payment.presentation.dto.charge.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Payment(groupId = "UPI", mode = "inApp")
public class CollectInAppSeamlessUpiPaymentChargingResponse extends SeamlessUpiPaymentChargingResponse {
}