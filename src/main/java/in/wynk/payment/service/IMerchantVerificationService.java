package in.wynk.payment.service;

import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantVerificationService {

    <T> BaseResponse<T> doVerify(VerificationRequest verificationRequest);
}
