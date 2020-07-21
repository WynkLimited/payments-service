package in.wynk.payment.service;

import in.wynk.payment.core.dto.request.ChargingStatusRequest;
import in.wynk.payment.core.dto.response.BaseResponse;

public interface IMerchantPaymentStatusService {

    BaseResponse<?> status(ChargingStatusRequest chargingStatusRequest);

}
