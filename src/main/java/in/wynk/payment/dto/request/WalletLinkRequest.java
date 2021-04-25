package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLinkRequest {

    private String encSi;
    private PaymentCode paymentCode;

}
