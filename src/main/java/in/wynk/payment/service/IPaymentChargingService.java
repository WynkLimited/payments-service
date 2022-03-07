package in.wynk.payment.service;

import in.wynk.payment.dto.common.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.request.AbstractChargingRequest;

public interface IPaymentChargingService<R extends AbstractPaymentChargingResponse, T extends AbstractChargingRequest<?>> {

    R charge(T request);

}
