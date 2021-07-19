package in.wynk.payment.dto.phonepe.autodebit;

import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PhonePeAutoDebitCallbackRequestPayload extends CallbackRequest {

    private String transactionId;
    private String phonePeVersionCode;

}