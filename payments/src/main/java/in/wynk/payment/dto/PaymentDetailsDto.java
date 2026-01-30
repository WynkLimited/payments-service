package in.wynk.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailsDto {
    private String couponId;
    private String paymentId;
    private String paymentMode;
    private String merchantName;
    private boolean autoRenew;
    private boolean trialOpted;
}
