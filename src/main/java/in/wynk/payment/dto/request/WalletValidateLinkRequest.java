package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletValidateLinkRequest {

    private String otp;
    @Setter
    private String state_token;
    private PaymentCode paymentCode;

}
