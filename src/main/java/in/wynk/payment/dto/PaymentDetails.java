package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import lombok.*;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDetails implements IPaymentDetails {
    private String couponId;
    private String paymentId;
    @Analysed(name = "paymentMode")
    private String paymentMode;
    @Analysed(name = "bankName")
    private String merchantName;
    private boolean trialOpted;
    private boolean autoRenew;
}
