package in.wynk.payment.presentation.dto.charge.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class UpiPaymentChargingResponse extends PaymentChargingResponse {
}