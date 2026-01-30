package in.wynk.payment.dto.phonepe;

import lombok.Getter;

@Getter
public class PhonePeTransactionResponseWrapper implements PhonePeResponse.PhonePeResponseWrapper {
    private String transactionId;
    private String merchantId;
    private String providerReferenceId;
    private Long amount;
    private String paymentState;
    private String payResponseCode;
}
