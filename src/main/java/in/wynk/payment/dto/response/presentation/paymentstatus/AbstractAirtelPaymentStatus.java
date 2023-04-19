package in.wynk.payment.dto.response.presentation.paymentstatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.response.AbstractChargingStatusResponseV2;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AbstractAirtelPaymentStatus extends AbstractChargingStatusResponseV2 {
}
