package in.wynk.payment.dto.aps.option;

import in.wynk.payment.dto.aps.common.ApsHealthCheckConfig;
import in.wynk.payment.dto.aps.common.HealthStatus;
import in.wynk.payment.dto.aps.common.PaymentOption;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@ToString
public class ApsEligiblePaymentMethodResponse {
    private List<PaymentOption> paymentOptions;
    private List<PaymentOption> recentPaymentOptions;
    private List<PaymentOption> promotedPaymentOptions;
    private List<PaymentOption> migratedPaymentOptions;
    private Map<HealthStatus, ApsHealthCheckConfig> healthCheckConfig;
}
