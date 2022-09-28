package in.wynk.payment.utils;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PageResponseDetails;
import in.wynk.payment.dto.gpbs.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;
import in.wynk.session.context.SessionContextHolder;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;

/**
 * @author Nishesh Pandey
 */
public class MerchantServiceUtil {

    public static GooglePlayBillingResponse.GooglePlayBillingData getUrl (Transaction transaction, LatestReceiptResponse latestReceiptResponse,
                                                                          GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder){
        if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
            GooglePlayLatestReceiptResponse googlePlayVerificationResponse = (GooglePlayLatestReceiptResponse) latestReceiptResponse;

            GooglePlayPaymentDetails paymentDetails = GooglePlayPaymentDetails.builder().valid(true).orderId(googlePlayVerificationResponse.getSubscriptionId()).purchaseToken(googlePlayVerificationResponse.getPurchaseToken()).build();
            builder.paymentDetails(paymentDetails);
            if (latestReceiptResponse != null && latestReceiptResponse.getSuccessUrl() != null) {
                builder.pageDetails(PageResponseDetails.builder().pageUrl(latestReceiptResponse.getSuccessUrl()).build());
            } else {
                addSuccessUrl(builder);
            }
        } else {
            if (latestReceiptResponse.getFailureUrl() != null) {
                builder.pageDetails(PageResponseDetails.builder().pageUrl(latestReceiptResponse.getFailureUrl()).build());
            } else {
                addFailureUrl(builder);
            }
        }
        return builder.build();
    }

    public static PaymentEvent getGooglePlayEvent (GooglePlayVerificationRequest gRequest, GooglePlayLatestReceiptResponse gResponse) {
        //if call is for linkedPurchaseToken, payment event should be cancelled for this old purchaseToken
        if(Objects.nonNull(gResponse.getGooglePlayResponse().getLinkedPurchaseToken()) && gResponse.getPurchaseToken().equalsIgnoreCase(gResponse.getGooglePlayResponse().getLinkedPurchaseToken())){
            return PaymentEvent.CANCELLED;
        }
        Integer notificationType = gRequest.getPaymentDetails().getNotificationType();
        switch (notificationType) {
            case 1:
            case 2:
            case 7:
            case 8:
                return PaymentEvent.RENEW;
            case 3:
                if (Long.parseLong(gResponse.getGooglePlayResponse().getExpiryTimeMillis()) < System.currentTimeMillis()) {
                    return PaymentEvent.CANCELLED;
                } else {
                    return PaymentEvent.UNSUBSCRIBE;
                }
            case 4:
                return PaymentEvent.PURCHASE;
            case 5:
            case 10:
                return PaymentEvent.CANCELLED;
            case 9:
                return PaymentEvent.DEFERRED;
            case 12:
                return PaymentEvent.UNSUBSCRIBE;
        }
        throw new WynkRuntimeException("This event is not supported");
    }

    private static void addFailureUrl (GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
        String failure_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}");
        builder.pageDetails(PageResponseDetails.builder().pageUrl(new StringBuilder(failure_url).append(SessionContextHolder.getId())
                .append(SLASH)
                .append(sessionDTO.<String>get(OS))
                .append(QUESTION_MARK)
                .append(SERVICE)
                .append(EQUAL)
                .append(sessionDTO.<String>get(SERVICE))
                .append(AND)
                .append(BUILD_NO)
                .append(EQUAL)
                .append(sessionDTO.<Integer>get(BUILD_NO))
                .toString()).build());
    }

    public static void addSuccessUrl (GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
        String success_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"), "${payment.success.page}");
        builder.pageDetails(PageResponseDetails.builder().pageUrl(new StringBuilder(success_url).append(SessionContextHolder.getId())
                .append(SLASH)
                .append(sessionDTO.<String>get(OS))
                .append(QUESTION_MARK)
                .append(SERVICE)
                .append(EQUAL)
                .append(sessionDTO.<String>get(SERVICE))
                .append(AND)
                .append(BUILD_NO)
                .append(EQUAL)
                .append(sessionDTO.<Integer>get(BUILD_NO))
                .toString()).build());
    }
}