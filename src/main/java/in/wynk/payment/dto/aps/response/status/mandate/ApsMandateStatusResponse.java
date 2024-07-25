package in.wynk.payment.dto.aps.response.status.mandate;

import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ApsMandateStatusResponse {
    private String status;
    private String action;
    private String amount;
    private String authpayuid;
    private String mandateEndDate;
    private String mandateStartDate;
}
