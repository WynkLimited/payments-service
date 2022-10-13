package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;
import in.wynk.payment.dto.response.LatestReceiptResponse;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class IapVerificationRequestV2Wrapper {
    private final IapVerificationRequestV2 iapVerificationV2;
    private final LatestReceiptResponse latestReceiptResponse;
}
