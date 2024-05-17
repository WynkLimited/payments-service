package in.wynk.payment.utils;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PageResponseDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayProductReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlaySubscriptionReceiptResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;
import in.wynk.session.context.SessionContextHolder;

import java.util.Map;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.dto.gpbs.GooglePlayConstant.*;

/**
 * @author Nishesh Pandey
 */
public class MerchantServiceUtil {

    public static GooglePlayBillingResponse.GooglePlayBillingData getUrl (Transaction transaction, LatestReceiptResponse latestReceiptResponse,
                                                                          GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Map<String, Object> payload = null;
        if (Objects.nonNull(sessionDTO)) {
            payload = sessionDTO.getSessionPayload();
        }
        if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
            GooglePlayLatestReceiptResponse googlePlayVerificationResponse = (GooglePlayLatestReceiptResponse) latestReceiptResponse;

            GooglePlayPaymentDetails paymentDetails = GooglePlayPaymentDetails.builder().valid(true).orderId(googlePlayVerificationResponse.getSubscriptionId())
                    .purchaseToken(googlePlayVerificationResponse.getPurchaseToken()).build();
            builder.paymentDetails(paymentDetails);

            if (latestReceiptResponse.getSuccessUrl() != null || Objects.nonNull(payload.get("successWebUrl"))) {
                builder.pageDetails(PageResponseDetails.builder().pageUrl(Objects.nonNull(latestReceiptResponse.getSuccessUrl()) ? latestReceiptResponse.getSuccessUrl()
                        : (String) payload.get("successWebUrl")).build());
            } else {
                addSuccessUrl(builder);
            }
        } else {
            if (latestReceiptResponse.getFailureUrl() != null || Objects.nonNull(payload.get("failureWebUrl"))) {
                builder.pageDetails(
                        PageResponseDetails.builder().pageUrl(Objects.nonNull(latestReceiptResponse.getFailureUrl()) ? latestReceiptResponse.getFailureUrl() : (String) payload.get("failureWebUrl"))
                                .build());
            } else {
                addFailureUrl(builder);
            }
        }
        return builder.build();
    }

    public static PaymentEvent getGooglePlayEvent (GooglePlayVerificationRequest gRequest, GooglePlayLatestReceiptResponse gResponse, String productType) {
        //if call is for linkedPurchaseToken, payment event should be cancelled for this old purchaseToken
        Integer notificationType = gRequest.getPaymentDetails().getNotificationType();
        if (BaseConstants.POINT.equals(productType)) {
            GooglePlayProductReceiptResponse productReceiptResponse = (GooglePlayProductReceiptResponse) gResponse.getGooglePlayResponse();
            if (productReceiptResponse.getPurchaseState() == 0) {
                return PaymentEvent.POINT_PURCHASE;

            } else if (productReceiptResponse.getPurchaseState() == 1) {
                return PaymentEvent.CANCELLED;
            }
        } else {
            GooglePlaySubscriptionReceiptResponse subscriptionReceiptResponse = ((GooglePlaySubscriptionReceiptResponse) gResponse.getGooglePlayResponse());
            if (Objects.nonNull(subscriptionReceiptResponse.getLinkedPurchaseToken()) && gResponse.getPurchaseToken().equalsIgnoreCase(subscriptionReceiptResponse.getLinkedPurchaseToken())) {
                return PaymentEvent.CANCELLED;
            }

            switch (notificationType) {
                case 1:
                case 2:
                case 7:
                case 8:
                    return PaymentEvent.RENEW;
                case 3:
                    if (Long.parseLong(subscriptionReceiptResponse.getExpiryTimeMillis()) < System.currentTimeMillis()) {
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
        throw new WynkRuntimeException("This product type is not supported");
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

    public static boolean getAutoRenewalOpted (GooglePlayVerificationRequest gRequest, LatestReceiptResponse receiptResponse) {
        switch (gRequest.getPaymentDetails().getNotificationType()) {
            case 1:
            case 2:
            case 4:
            case 5:
            case 9:
            case 13:
                return receiptResponse.isAutoRenewal();
            case 6:
            case 7:
            case 8:
            case 10:
            case 11:
                return true;
            case 3:
            case 12:
                return false;
        }
        throw new WynkRuntimeException("This event is not supported");

    }

    public static String getService (String packageName) {
        if (MUSIC_PACKAGE_NAME.equals(packageName)) {
            return SERVICE_MUSIC;
        } else if (AIRTEL_TV_PACKAGE_NAME.equals(packageName)) {
            return SERVICE_AIRTEL_TV;
        }
        return null;
    }

    public static String getPackageFromService (String service) {
        switch (service) {
            case SERVICE_MUSIC:
                return MUSIC_PACKAGE_NAME;
            case SERVICE_AIRTEL_TV:
                return AIRTEL_TV_PACKAGE_NAME;
            default:
                throw new RuntimeException("Service mapping is not present for the package name");
        }
    }
}