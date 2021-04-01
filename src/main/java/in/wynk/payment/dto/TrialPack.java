package in.wynk.payment.dto;

import in.wynk.subscription.common.enums.ProvisionType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TrialPack extends AbstractPack {

    private final PaidPack paidPack;

    @Override
    public ProvisionType getType() {
        return ProvisionType.FREE;
    }

    public double getAmount() {
        return 0;
    }

}
