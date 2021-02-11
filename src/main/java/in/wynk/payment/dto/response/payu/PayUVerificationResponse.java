package in.wynk.payment.dto.response.payu;

import com.google.gson.annotations.SerializedName;
import in.wynk.payment.dto.payu.AbstractPayUTransactionDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayUVerificationResponse<T extends AbstractPayUTransactionDetails> {

    private long status;

    @SerializedName("msg")
    private String message;

    @SerializedName("transaction_details")
    private Map<String, T> transactionDetails;

    public T getTransactionDetails(String transactionId) {
        return (T) transactionDetails.get(transactionId);
    }
}
