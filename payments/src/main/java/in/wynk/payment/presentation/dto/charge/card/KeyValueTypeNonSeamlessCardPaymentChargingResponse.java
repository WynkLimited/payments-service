package in.wynk.payment.presentation.dto.charge.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Payment(groupId = "CARD", mode = "keyValue")
public class KeyValueTypeNonSeamlessCardPaymentChargingResponse extends NonSeamlessCardPaymentChargingResponse {
    @JsonProperty("info")
    private String form;
    @JsonProperty("redirectUrl")
    private String url;
}