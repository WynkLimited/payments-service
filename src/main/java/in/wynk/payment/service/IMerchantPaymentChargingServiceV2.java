package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;

/**
 * @author Nishesh Pandey
 */
public interface IMerchantPaymentChargingServiceV2<R extends AbstractCoreChargingResponse, T extends AbstractChargingRequestV2> {
    R charge(T request);
}
