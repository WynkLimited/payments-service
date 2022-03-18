package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.core.ParameterizedTypeReference;

import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.CacheBeanNameConstants.COUPON;
import static in.wynk.common.constant.CacheBeanNameConstants.PAYMENT_METHOD;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetails extends AbstractPaymentDetails {

    @Analysed
    @MongoBaseEntityConstraint(beanName = COUPON)
    private String couponId;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = PAYMENT_METHOD)
    private String paymentId;

    @Analysed(name = "paymentMode")
    private String paymentMode;

    @Analysed(name = "bankName")
    private String merchantName;

    @Analysed
    private boolean autoRenew;

    @Analysed
    private boolean trialOpted;

    public boolean isTrialOpted() {
        return BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<PaymentMethod, String>>() {
        }).get(paymentId).isTrialSupported() && trialOpted;
    }

    @Override
    public String getPaymentGroup() {
        return paymentMode;
    }

}