package in.wynk.payment.dto.aps.response.charge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractExternalChargingResponse {
    private String pgId;
    private String orderId;
    private String paymentGateway;
    private String paymentStatus;
    private String gatewayErrorMessage;
    private String pgSystemOrderId;
    private String pgSystemId;
    private String merchantId;
    private String merchantKey;
}
