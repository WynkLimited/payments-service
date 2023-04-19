package in.wynk.payment.presentation.dto.charge.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Payment(groupId = "CARD", mode = "inApp")
public class InAppSeamlessCardPaymentChargingResponse extends SeamlessCardPaymentChargingResponse {
}