package in.wynk.payment.service;

import in.wynk.payment.core.dto.request.IapVerificationRequest;
import in.wynk.payment.core.dto.response.BaseResponse;

public interface IMerchantIapPaymentVerificationService {

    BaseResponse<?> verifyReceipt(IapVerificationRequest iapVerificationRequest);

}
