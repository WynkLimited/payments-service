package in.wynk.payment.dto.phonepe.autodebit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhonePeAutoDebitDoChargingRequest {
    private String merchantId;
    private String userAuthToken;
    private String transactionId;
    private long amount;
    private DeviceContext deviceContext;

}
