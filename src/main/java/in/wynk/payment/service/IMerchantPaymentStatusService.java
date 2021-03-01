package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentStatusService {

    BaseResponse<?> status(AbstractTransactionStatusRequest transactionStatusRequest);

}
