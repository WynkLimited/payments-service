package in.wynk.payment.dto.aps.charge;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public abstract class ApsExternalChargingResponse {
    private String pgId;
    private String orderId;
    private String paymentGateway;
    private String paymentStatus;
    private String successUrl;
    private String errorUrl;
    private boolean inAppOtpApplicable;
}
