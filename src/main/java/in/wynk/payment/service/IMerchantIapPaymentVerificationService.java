package in.wynk.payment.service;

import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantIapPaymentVerificationService {

    <T> BaseResponse<T> verifyIap(IapVerificationRequest iapVerificationRequest);

}
