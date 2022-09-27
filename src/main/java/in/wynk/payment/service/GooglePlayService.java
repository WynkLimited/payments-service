package in.wynk.payment.service;

import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;

/**
 * @author Nishesh Pandey
 */
public interface GooglePlayService {
    BaseResponse<GooglePlayBillingResponse> verifyRequest (GooglePlayVerificationRequest googlePlayVerificationRequest);

    void acknowledgeSubscription (IapVerificationRequestV2 request, LatestReceiptResponse latestReceiptResponse);
}
