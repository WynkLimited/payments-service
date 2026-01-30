package in.wynk.payment.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayUVerificationResponse<T> {

    private long status;

    @JsonProperty("msg")
    private String message;

    @JsonProperty("transaction_details")
    private Map<String, T> transactionDetails;

    public T getTransactionDetails(String transactionId) {
        return transactionDetails.get(transactionId);
    }
}
