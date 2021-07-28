package in.wynk.payment.dto.phonepe.autodebit;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PhonePeAutoDebitTopUpExternalRequest {

    private long amount;
    private String merchantId;
    private String userAuthToken;
    private String linkType;
    private boolean adjustAmount;
    private DeviceContext deviceContext;
}
