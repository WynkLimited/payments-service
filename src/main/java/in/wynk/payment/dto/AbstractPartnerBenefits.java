package in.wynk.payment.dto;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
public abstract class AbstractPartnerBenefits {

    private final String id;
    private final String name;
    private final String logo;
    private final String icon;
    private final List<String> rails;

    public abstract String getType();
}
