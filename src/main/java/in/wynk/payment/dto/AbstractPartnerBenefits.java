package in.wynk.payment.dto;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractPartnerBenefits {
    private final String title;
    private final String name;
    private final String logo;
    private final String icon;

    public abstract String getType();
}
