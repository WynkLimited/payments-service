package in.wynk.payment.service;

import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentChargingService {

    BaseResponse<?> doCharging(ChargingRequest chargingRequest);

}
