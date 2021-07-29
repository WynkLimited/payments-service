package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    private final int monthlyAmount;
    private final int month;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private final double dailyAmount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private final int day;

    private final AbstractPartnerBenefits benefits;

    public abstract ProvisionType getType();

}
