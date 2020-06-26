package in.wynk.payment.service;

import in.wynk.payment.dto.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentVerificationService {

    <T> BaseResponse<T> doVerify(VerificationRequest verificationRequest);

}
