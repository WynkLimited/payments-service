package in.wynk.payment.dto.paytm;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PaytmCallbackRequestPayload extends CallbackRequest {
    @JsonProperty("STATUS")
    private String status;
    private String transactionId;
}