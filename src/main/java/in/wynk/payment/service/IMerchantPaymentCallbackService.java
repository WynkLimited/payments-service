package in.wynk.payment.service;

import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentCallbackService {

    BaseResponse<?> handleCallback(CallbackRequest callbackRequest);

}
