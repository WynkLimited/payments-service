package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentStatusService {

    BaseResponse<?> status(ChargingStatusRequest chargingStatusRequest, Transaction transaction);

}
