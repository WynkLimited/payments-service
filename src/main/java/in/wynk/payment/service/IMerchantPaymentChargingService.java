package in.wynk.payment.service;

import in.wynk.payment.core.dto.request.ChargingRequest;
import in.wynk.payment.core.dto.response.BaseResponse;

public interface IMerchantPaymentChargingService {

    BaseResponse<?>  doCharging(ChargingRequest chargingRequest);

}
