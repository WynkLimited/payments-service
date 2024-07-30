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
    private String pgId;
    private String mandateId;
    private String customerId;
    private String status;
    private String endDate;
}
