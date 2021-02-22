package in.wynk.payment.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.payu.AbstractPayUTransactionDetails;
import lombok.Getter;

import java.util.Map;

@Getter
public class PayUVerificationResponse<T extends AbstractPayUTransactionDetails> {

    private long status;

    @JsonProperty("msg")
    private String message;

    @JsonProperty("transaction_details")
    private Map<String, T> transactionDetails;

    public T getTransactionDetails(String transactionId) {
        return transactionDetails.get(transactionId);
    }
}
