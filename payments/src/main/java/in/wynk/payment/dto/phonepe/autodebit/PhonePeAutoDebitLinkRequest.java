package in.wynk.payment.dto.phonepe.autodebit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhonePeAutoDebitLinkRequest {
    private final String merchantId;
    private final String mobileNumber;
}
