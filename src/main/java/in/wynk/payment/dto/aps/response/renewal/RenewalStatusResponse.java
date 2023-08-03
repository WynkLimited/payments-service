package in.wynk.payment.dto.aps.response.renewal;

import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class RenewalStatusResponse extends ApsChargeStatusResponse {
    private String pgSystemId;
}

