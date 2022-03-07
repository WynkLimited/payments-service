package in.wynk.payment.service;

import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import in.wynk.payment.dto.request.AbstractChargingRequest;

public interface IPaymentChargingService<R extends AbstractChargingGatewayResponse, T extends AbstractChargingRequest<?>> {

    R charge(T request);

}
