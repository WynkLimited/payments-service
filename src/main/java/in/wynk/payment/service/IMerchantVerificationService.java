package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.VerificationResponse;

public interface IMerchantVerificationService {
    WynkResponseEntity<VerificationResponse> doVerify(VerificationRequest verificationRequest);
}