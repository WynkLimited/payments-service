package in.wynk.payment.dto.gateway.card;

import in.wynk.payment.constant.FlowType;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "CARD", mode = "INAPP")
public class CardInAppChargingResponse extends AbstractSeamlessCardChargingResponse{
}
