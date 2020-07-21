package in.wynk.payment.service;

import in.wynk.payment.core.dto.request.VerificationRequest;
import in.wynk.payment.core.dto.response.BaseResponse;

public interface IMerchantVerificationService {

    BaseResponse<?> doVerify(VerificationRequest verificationRequest);
}
