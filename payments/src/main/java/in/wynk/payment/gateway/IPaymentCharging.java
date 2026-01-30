package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;

public interface IPaymentCharging<R extends AbstractPaymentChargingResponse, T extends AbstractPaymentChargingRequest> {

    R charge(T request);

}
