package in.wynk.payment.service;

import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.IapVerificationRequestV2Wrapper;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;

/**
 * @author Nishesh Pandey
 */
public interface IMerchantIapPaymentPreVerificationService {

    void verifyRequest (IapVerificationRequestV2Wrapper iapVerificationRequestV2Wrapper);
}
