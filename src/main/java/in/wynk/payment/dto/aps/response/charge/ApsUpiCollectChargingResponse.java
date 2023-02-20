package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class ApsUpiCollectChargingResponse extends AbstractApsUpiChargingResponse {
   private boolean inAppUPIFlow;

}
