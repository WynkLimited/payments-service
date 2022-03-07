package in.wynk.payment.dto.aps.charge;


import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.aps.common.PollConfig;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class ApsUpiIntentChargingChargingResponse extends AbstractApsExternalChargingResponse {
    @JsonProperty("isApbDirectUpi")
    private boolean apbDirectUpi;
    private String upiLink;
    private PollConfig pollingConfig;
}
