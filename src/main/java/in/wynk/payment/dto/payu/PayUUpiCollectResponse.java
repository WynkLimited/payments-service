package in.wynk.payment.dto.payu;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PayUUpiCollectResponse extends AbstractPayUUpiResponse<PayUUpiCollectResponse.CollectResult> {

    @Getter
    @NoArgsConstructor
    public final class CollectResult extends AbstractPayUUpiResponse.Result {
        private String otpPostUrl;
    }

}
