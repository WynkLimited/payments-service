package in.wynk.payment.dto.aps.response.charge;

import in.wynk.payment.dto.aps.common.PollingConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class AbstractUpiChargingResponse extends AbstractExternalChargingResponse {
    private boolean hyperSdk;
    private boolean isApbDirectUpi;
    private PollingConfig pollingConfig;
}
