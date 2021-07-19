package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;

public interface IMerchantPaymentStatusService<R extends AbstractChargingStatusResponse, T extends AbstractTransactionStatusRequest> {

    WynkResponseEntity<R> status(T request);

}
