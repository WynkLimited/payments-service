package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.subscription.common.enums.ProvisionType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractPack {

    private final long period;
    private final double amount;

    private final String title;
    private final String timeUnit;

    private final int perMonthValue;
    private final int month;

    private final Double dailyAmount;
    private final Integer day;
    private final String currency;
    private final boolean isCombo;

    private final AbstractPartnerBenefits benefits;

    public abstract ProvisionType getType();

}
