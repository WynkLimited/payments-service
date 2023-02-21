package in.wynk.payment.service;

import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequestV2;

public interface IVerificationService<R extends AbstractVerificationResponse, T extends VerificationRequestV2> {
    R verify (T verificationRequest);
}
