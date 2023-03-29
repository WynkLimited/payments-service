package in.wynk.payment.dto.aps.response.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.aps.common.PollConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class AbstractApsUpiChargingResponse extends AbstractApsExternalChargingResponse {
    private boolean hyperSdk;
    private boolean isApbDirectUpi;
    private PollConfig pollingConfig;
}
