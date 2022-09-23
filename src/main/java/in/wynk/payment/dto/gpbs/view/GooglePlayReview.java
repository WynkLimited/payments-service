package in.wynk.payment.dto.gpbs.view;

import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;

/**
 * @author Nishesh Pandey
 */
public interface GooglePlayReview {
    GooglePlayBillingResponse getGooglePlayResponse (GooglePlayVerificationRequest request, GooglePlayReceiptResponse googlePlayReceipt);
}
