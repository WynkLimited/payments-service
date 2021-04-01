package in.wynk.payment.dto.phonepe;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhonePeRefundRequest {
      private final long amount;
      private final String message;
      private final String merchantId;
      private final String subMerchant;
      private final String transactionId;
      private final String merchantOrderId;
      private final String providerReferenceId;
      private final String originalTransactionId;
}
