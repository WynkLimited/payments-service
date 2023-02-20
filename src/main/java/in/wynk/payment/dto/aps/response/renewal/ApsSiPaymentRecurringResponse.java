package in.wynk.payment.dto.aps.response.renewal;

import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.response.charge.AbstractApsExternalChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
public class ApsSiPaymentRecurringResponse extends AbstractApsExternalChargingResponse {
    private ApsApiResponseWrapper<ApsRenewalStatusResponse> body;
    private String statusCode;
    private Integer statusCodeValue;
}
