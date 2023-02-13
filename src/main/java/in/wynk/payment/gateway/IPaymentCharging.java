package in.wynk.payment.gateway;

import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import in.wynk.payment.dto.request.AbstractChargingRequest;

public interface IPaymentCharging<R extends AbstractChargingGatewayResponse, T extends AbstractChargingRequest<?>> {

    R charge(T request);

}
