package in.wynk.payment.dto;

import in.wynk.subscription.common.enums.ProvisionType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractPack {

    private final long period;
    private final double amount;

    private final String title;
    private final String timeUnit;

    private final AbstractPartnerBenefits benefits;

    public abstract ProvisionType getType();

}
