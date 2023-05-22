package in.wynk.payment.dto.aps.response.renewal;

import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
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
public class SiPaymentRecurringResponse {
    private ApsApiResponseWrapper<SiRecurringData> body;
    private String statusCode;
    private Integer statusCodeValue;
}