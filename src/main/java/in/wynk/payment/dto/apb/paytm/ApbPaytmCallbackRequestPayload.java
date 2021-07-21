package in.wynk.payment.dto.apb.paytm;

import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ApbPaytmCallbackRequestPayload extends CallbackRequest {
    private String transactionId;
}