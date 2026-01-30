package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
public class PhonePePaymentRequest {

    private final String merchantId;
    private final String transactionId;
    private final String merchantUserId;
    private final long amount;
    private final String merchantOrderId;
    private final String mobileNumber;
    private final String message;
    private final String subMerchant;
    private final String email;
    private final String shortName;

}
