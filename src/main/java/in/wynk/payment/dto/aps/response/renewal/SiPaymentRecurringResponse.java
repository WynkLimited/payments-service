package in.wynk.payment.dto.aps.response.renewal;

import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SiPaymentRecurringResponse {
    private String orderId;
    private String pgId;
    private String pgSystemId;
    private String pgStatus;
    private String paymentGateway;
    private String paymentMode;
}