package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class UpiCollectChargingResponse extends AbstractUpiChargingResponse {
   private boolean inAppUPIFlow;

}
