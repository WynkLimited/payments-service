package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAddMoneyRequest {

    private int planId;
    private String itemId;
    @Setter
    private String deviceId;
    private double amountToCredit;
    private PaymentCode paymentCode;
    private long phonePeVersionCode;

}