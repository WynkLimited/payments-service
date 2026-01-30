package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.CacheBeanNameConstants.COUPON;
import static in.wynk.common.constant.CacheBeanNameConstants.PLAN_DTO;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class WalletBalanceRequest extends WalletRequest {

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = PLAN_DTO)
    private Integer planId;

    @Analysed
    @MongoBaseEntityConstraint(beanName = COUPON)
    private String couponId;

    public abstract String getUid();

    public abstract String getMsisdn();

    public abstract String getService();

}