package in.wynk.payment.dto.aps.charge;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractApsExternalChargingResponse {
    private String pgId;
    private String pgSystemOrderId;
    private String pgSystemId;
    private String merchantId;
    private String merchantKey;
    private String orderId;
    private String paymentGateway;
    private String paymentStatus;
    private String successUrl;
    private String errorUrl;
    private boolean inAppOtpApplicable;
}
