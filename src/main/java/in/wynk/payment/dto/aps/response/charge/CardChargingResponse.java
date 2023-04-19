package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@NoArgsConstructor
@SuperBuilder
public class CardChargingResponse extends ApsFormSubmitChargingResponse {
    private boolean directOtpApplicable;
    private String successUrl;
    private String errorUrl;
}
