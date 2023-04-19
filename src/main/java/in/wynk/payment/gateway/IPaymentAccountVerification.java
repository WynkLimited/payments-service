package in.wynk.payment.gateway;

import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.AbstractVerificationRequest;

public interface IPaymentAccountVerification<R extends AbstractVerificationResponse, T extends AbstractVerificationRequest> {
    R verify(T request);
}
