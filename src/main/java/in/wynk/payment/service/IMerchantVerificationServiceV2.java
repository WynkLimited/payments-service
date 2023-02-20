package in.wynk.payment.service;

import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentVerificationResponse;
import in.wynk.payment.dto.request.VerificationRequest;

/**
 * @author Nishesh Pandey
 */
public interface IMerchantVerificationServiceV2<R extends AbstractPaymentInstrumentVerificationResponse, T extends VerificationRequest> {
     R doVerify(T verificationRequest);
}
