package in.wynk.payment.dto.aps.response.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.aps.common.PollConfig;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class AbstractApsUpiChargingResponse extends AbstractApsExternalChargingResponse {
    private boolean hyperSdk;
    @JsonProperty("isApbDirectUpi")
    private boolean apbDirectUpi;
    private PollConfig pollingConfig;
}
