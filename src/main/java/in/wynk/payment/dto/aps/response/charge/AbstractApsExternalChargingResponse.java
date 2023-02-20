package in.wynk.payment.dto.aps.response.charge;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractApsExternalChargingResponse {
    //Card data Start
    private String pgId;
    private String orderId;
    private String paymentGateway;
    private String paymentStatus;
    private String gatewayErrorMessage;
    //Card data end
    private String pgSystemOrderId;
    private String pgSystemId;
    private String merchantId;
    private String merchantKey;



}
