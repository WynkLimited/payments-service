package in.wynk.payment.dto.paytm;

import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PaytmCallbackRequestPayload extends CallbackRequest {
    private String transactionId;
}