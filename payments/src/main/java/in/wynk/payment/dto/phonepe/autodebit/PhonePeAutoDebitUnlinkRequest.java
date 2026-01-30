package in.wynk.payment.dto.phonepe.autodebit;

import lombok.Builder;
import lombok.Getter;
@Builder
@Getter
public class PhonePeAutoDebitUnlinkRequest {
    private String merchantId;
    private String userAuthToken;
}
