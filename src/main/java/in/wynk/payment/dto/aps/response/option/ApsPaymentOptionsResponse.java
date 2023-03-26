package in.wynk.payment.dto.aps.response.option;

import in.wynk.common.dto.IErrorDetails;
import in.wynk.payment.dto.aps.common.ApsHealthCheckConfig;
import in.wynk.payment.dto.aps.common.HealthStatus;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@ToString
public class ApsPaymentOptionsResponse implements IErrorDetails {
    private PaymentOptionsConfig config;
    private SavedUserOptions savedUserOptions;
    private List<PaymentOptions> payOptions;
    //private Map<HealthStatus, ApsHealthCheckConfig> healthCheckConfig;
}
