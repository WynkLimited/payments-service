package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor
public abstract class ApsFormSubmitChargingResponse extends AbstractExternalChargingResponse {
    private String html;
}
