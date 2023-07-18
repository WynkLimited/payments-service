package in.wynk.payment.dto.aps.response.renewal;

import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.response.charge.AbstractExternalChargingResponse;
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
@NoArgsConstructor
public class SiPaymentRecurringResponse extends AbstractExternalChargingResponse {
    private ApsApiResponseWrapper<ApsChargeStatusResponse> body;
    private String statusCode;
    private Integer statusCodeValue;
}
