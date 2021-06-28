package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDetails implements IPaymentDetails {
    private String couponId;
    @Analysed(name = "paymentMode")
    private String paymentMode;
    @Analysed(name = "bankName")
    private String merchantName;
    private String paymentCode;
    private boolean trialOpted;
    private boolean autoRenew;
}
