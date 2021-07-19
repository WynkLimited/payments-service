package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import lombok.*;
import org.springframework.core.ParameterizedTypeReference;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDetails implements IPaymentDetails {
    @Analysed
    private String couponId;
    @Analysed
    private String paymentId;
    @Analysed(name = "paymentMode")
    private String paymentMode;
    @Analysed(name = "bankName")
    private String merchantName;
    @Analysed
    private boolean trialOpted;
    @Analysed
    private boolean autoRenew;

    public boolean isTrialOpted() {
        return BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<PaymentMethod, String>>() {
        }).get(paymentId).isTrialSupported() && trialOpted;
    }

}
