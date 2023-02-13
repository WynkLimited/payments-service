package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentChargingServiceV2<R extends AbstractCoreChargingResponse, T extends AbstractChargingRequestV2>{
    R chargeV2(T request);
}
