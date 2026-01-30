package in.wynk.payment.dto.manager;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class ChargingGatewayResponseWrapper<T extends AbstractChargingGatewayResponse> extends AbstractChargingGatewayResponse {
    private T pgResponse;
    private Transaction transaction;
    private IPurchaseDetails purchaseDetails;
}
