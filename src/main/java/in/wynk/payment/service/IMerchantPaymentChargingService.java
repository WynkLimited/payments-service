package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentChargingService<R extends AbstractChargingResponse, T extends AbstractChargingRequest<?>> {

    WynkResponseEntity<R> doCharging(T request);

}
