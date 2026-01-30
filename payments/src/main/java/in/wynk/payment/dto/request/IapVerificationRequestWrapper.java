package in.wynk.payment.dto.request;

import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IapVerificationRequestWrapper {
    private String clientId;
    private IapVerificationRequest verificationRequest;
    private LatestReceiptResponse receiptResponse;
    private IapVerificationRequestV2 verificationRequestV2;
}
