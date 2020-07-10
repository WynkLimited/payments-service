package in.wynk.payment.service;

import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantVerificationService {

    BaseResponse<?> doVerify(VerificationRequest verificationRequest);
}
