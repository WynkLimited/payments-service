package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractVerificationRequest;
import in.wynk.payment.dto.response.IVerificationResponse;

public interface IMerchantVerificationService {
    WynkResponseEntity<IVerificationResponse> doVerify(AbstractVerificationRequest verificationRequest);
}