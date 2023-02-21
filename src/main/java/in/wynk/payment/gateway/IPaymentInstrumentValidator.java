package in.wynk.payment.gateway;

import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.request.VerificationRequestV2;

public interface IPaymentInstrumentValidator<R extends AbstractVerificationResponse, T extends VerificationRequestV2> {
    R verify(T request);
}
