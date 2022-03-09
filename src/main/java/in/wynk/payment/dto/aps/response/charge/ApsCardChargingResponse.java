package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class ApsCardChargingResponse extends AbstractApsFormSubmitChargingResponse {
    private boolean directOtpApplicable;
}
