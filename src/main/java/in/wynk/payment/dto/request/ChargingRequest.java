package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

@ToString
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@AnalysedEntity
public class ChargingRequest {
    @Analysed
    private int planId;
    @Analysed
    private String itemId;
    @Analysed
    private boolean autoRenew;
    @Analysed
    private String couponId;
    @Analysed
    private PaymentCode paymentCode;
    @Analysed(name = "paymentMode")
    private String paymentMode;
    @Analysed(name = "bankName")
    private String merchantName;

}
