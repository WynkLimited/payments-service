package in.wynk.payment.service;

import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequest;

public interface IVerificationService<R extends AbstractVerificationResponse, T extends VerificationRequest> {
    R verify (T verificationRequest);
}
