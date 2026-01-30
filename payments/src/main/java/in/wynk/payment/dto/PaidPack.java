package in.wynk.payment.dto;

import in.wynk.subscription.common.enums.ProvisionType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PaidPack extends AbstractPack {

    @Override
    public ProvisionType getType() {
        return ProvisionType.PAID;
    }
}
