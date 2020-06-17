package in.wynk.payment.service;

import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentCallbackService {

    <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest);

}
