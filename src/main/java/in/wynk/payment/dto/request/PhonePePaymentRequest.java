package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
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

        public PhonePePaymentRequest(ChargingRequest chargingRequest){
                this.transactionId = chargingRequest.getTransactionId();
                this.amount = chargingRequest.getAmount() * 100; // PhonePe needs amount in paisa
                this.merchantUserId = chargingRequest.getUid();
        }
}
