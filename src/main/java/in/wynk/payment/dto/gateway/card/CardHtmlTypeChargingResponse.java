package in.wynk.payment.dto.gateway.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "CARD", mode = "HTML")
public class CardHtmlTypeChargingResponse extends AbstractNonSeamlessCardChargingResponse {
    @JsonProperty("info")
    private String html;
}
