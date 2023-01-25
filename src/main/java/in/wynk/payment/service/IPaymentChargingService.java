package in.wynk.payment.service;

import in.wynk.payment.dto.common.request.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.request.AbstractChargingRequest;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentChargingService<R extends AbstractPaymentChargingResponse, T extends AbstractChargingRequest<?>> {

    R charge(T request);

}
