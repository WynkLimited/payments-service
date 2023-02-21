package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class ApsCardChargingResponse extends AbstractApsFormSubmitChargingResponse {
    private boolean directOtpApplicable;
    private String successUrl;
    private String errorUrl;
}
