package in.wynk.payment.service;

import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;

/**
 * @author Nishesh Pandey
 */
public interface GooglePlayService {
    BaseResponse<GooglePlayBillingResponse> verifyReceiptDetails (GooglePlayVerificationRequest googlePlayVerificationRequest);
}
