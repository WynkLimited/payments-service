package in.wynk.payment.service;

import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentStatusService {

    <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest);

}
