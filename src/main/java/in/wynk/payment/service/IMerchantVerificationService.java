package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.IVerificationResponse;

public interface IMerchantVerificationService {
    WynkResponseEntity<IVerificationResponse> doVerify(VerificationRequest verificationRequest);
}