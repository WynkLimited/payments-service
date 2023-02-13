package in.wynk.payment.dto.response.gateway.card;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "CARD", mode = "keyValue")
public class CardKeyValueTypeChargingResponse extends AbstractNonSeamlessCardChargingResponse{
}
