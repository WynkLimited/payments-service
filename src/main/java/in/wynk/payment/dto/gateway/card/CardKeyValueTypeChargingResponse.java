package in.wynk.payment.dto.gateway.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "CARD", mode = "keyValue")
public class CardKeyValueTypeChargingResponse extends AbstractNonSeamlessCardChargingResponse {
    @JsonProperty("info")
    private Map<String, String> form;
}
