package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
public class PhonePePaymentRequest {

        private String merchantId;
        private String transactionId;
        private String merchantUserId;
        private Long amount;
        private String merchantOrderId;
        private String mobileNumber;
        private String message;
        private String subMerchant;
        private String email;
        private String shortName;

}
