package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SubPaymentOption extends AbstractPaymentOption {
    private String code;
    private boolean down;
    private boolean fluctuating;
    private HealthStatus healthStatus;
}
