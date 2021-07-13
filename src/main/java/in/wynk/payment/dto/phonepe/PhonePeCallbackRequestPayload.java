package in.wynk.payment.dto.phonepe;

import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PhonePeCallbackRequestPayload extends CallbackRequest{

    private String code;
    private String amount;
    private String checksum;
    private String merchantId;
    private String transactionId;
    private String providerReferenceId;

}