package in.wynk.payment.dto.aps.request.status.mandate;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
public class ApsMandateStatusRequest {
    private String mandateId;
    private String merchantConfigId;
}
