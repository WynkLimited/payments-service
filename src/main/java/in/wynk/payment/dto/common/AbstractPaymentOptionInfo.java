package in.wynk.payment.dto.common;

import in.wynk.payment.dto.aps.common.HealthStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractPaymentOptionInfo {
    private int order;
    private String id;
    private String title;
    @Builder.Default
    private String health = HealthStatus.UP.name();
    private boolean enabled;
    private boolean favorite;
    private boolean recommended;

    public abstract String getType();
}
