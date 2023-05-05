package in.wynk.payment.dto.aps.response.renewal;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class SiRecurringData extends ApsChargeStatusResponse {
}
