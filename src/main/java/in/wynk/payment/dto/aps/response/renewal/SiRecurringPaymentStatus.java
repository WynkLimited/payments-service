package in.wynk.payment.dto.aps.response.renewal;

import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class SiRecurringPaymentStatus extends ApsChargeStatusResponse {
    private String pgSystemId;
    private String paymentFrequency;
    private String lob;
    private String mid;
    private Integer circleId;
    private long paymentStartDate;
    private long paymentEndDate;
    private String mandateStatus;
    private String mandateId;
    private String nextRetry;
    private String redirectionUrl;
    private String paymentRoutedThrough;
}
