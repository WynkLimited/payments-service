package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/*
 * API for Card charging and Net bank charging is same and hence response is also same.
 * So no distinction for net Banking or card charging but flow will be redirect flow only
 */
@Getter
@ToString
@SuperBuilder
@NoArgsConstructor
public abstract class ApsFormSubmitChargingResponse extends AbstractApsExternalChargingResponse {
    private String html;
}
