package in.wynk.payment.service;

import in.wynk.payment.core.dto.request.CallbackRequest;
import in.wynk.payment.core.dto.response.BaseResponse;

public interface IMerchantPaymentCallbackService {

    BaseResponse<?> handleCallback(CallbackRequest callbackRequest);

}
