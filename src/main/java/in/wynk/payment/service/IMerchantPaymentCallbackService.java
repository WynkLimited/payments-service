package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentCallbackService<R extends AbstractCallbackResponse,T extends CallbackRequest> {

    WynkResponseEntity<R> handleCallback(T callbackRequest);

}
