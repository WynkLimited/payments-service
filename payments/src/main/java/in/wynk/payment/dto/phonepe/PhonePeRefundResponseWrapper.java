package in.wynk.payment.dto.phonepe;

import lombok.Getter;

@Getter
public class PhonePeRefundResponseWrapper implements PhonePeResponse.PhonePeResponseWrapper {
    private long amount;
    private String status;
    private String merchantId;
    private String mobileNumber;
    private String transactionId;
    private String payResponseCode;
    private String providerReferenceId;
}
