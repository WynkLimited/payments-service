package in.wynk.payment.dto.gpbs.view;

import in.wynk.payment.dto.PageResponseDetails;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Service
@RequiredArgsConstructor
public class GooglePlayReviewImpl implements GooglePlayReview {
    @Override
    public GooglePlayBillingResponse getGooglePlayResponse (GooglePlayVerificationRequest request, GooglePlayReceiptResponse googlePlayReceipt) {
        GooglePlayPaymentDetails paymentDetails= GooglePlayPaymentDetails.builder()
                .purchaseToken(request.getPaymentDetails().getPurchaseToken())
                .orderId(googlePlayReceipt.getOrderId())
                .valid(true)
                .build();
       /* PageResponseDetails pageResponse= PageResponseDetails.builder().pageUrl("test").build();
        final GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder = GooglePlayBillingResponse.GooglePlayBillingData.builder();
       GooglePlayBillingResponse.GooglePlayBillingData result = builder.paymentDetails(paymentDetails).pageResponseDetails(pageResponse).build();
        result.*/
        /*return GooglePlayBillingResponse.GooglePlayBillingResponseBuilder
                .builder().data().build();
                .paymentDetails(paymentDetails)
                .pageResponseDetails(pageResponse)
                .build();*/
        return null;
    }
}
