package in.wynk.payment.presentation.dto.charge.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class CardPaymentChargingResponse extends PaymentChargingResponse {
}