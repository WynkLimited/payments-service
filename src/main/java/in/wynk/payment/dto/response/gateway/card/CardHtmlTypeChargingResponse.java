package in.wynk.payment.dto.response.gateway.card;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "CARD", mode = "html")
public class CardHtmlTypeChargingResponse extends AbstractNonSeamlessCardChargingResponse{
}
