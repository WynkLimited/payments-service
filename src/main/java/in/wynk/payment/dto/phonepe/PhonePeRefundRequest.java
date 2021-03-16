package in.wynk.payment.dto.phonepe;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhonePeRefundRequest {
      private long amount;
      private String message;
      private String merchantId;
      private String subMerchant;
      private String transactionId;
      private String merchantOrderId;
      private String providerReferenceId;
      private String originalTransactionId;
}
