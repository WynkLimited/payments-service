package in.wynk.payment.core.constant;

import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class PaymentLoggingMarker extends BaseLoggingMarkers {
    public static final Marker PAYMENT_RECONCILIATION_FAILURE = MarkerFactory.getMarker("PAYMENT_RECONCILIATION_FAILURE");
    public static final Marker PAYMENT_RECONCILIATION_QUEUE = MarkerFactory.getMarker("PAYMENT_RECONCILIATION_QUEUE");
    public static final Marker PAYU_CHARGING_STATUS_VERIFICATION = MarkerFactory.getMarker("PAYU_CHARGING_STATUS_VERIFICATION");
    public static final Marker PAYU_CHARGING_STATUS_VERIFICATION_FAILURE = MarkerFactory.getMarker("PAYU_CHARGING_STATUS_VERIFICATION_FAILURE");
    public static final Marker PAYU_CHARGING_CALLBACK_FAILURE = MarkerFactory.getMarker("PAYU_CHARGING_CALLBACK_FAILURE");
    public static final Marker PAYU_RENEWAL_STATUS_ERROR = MarkerFactory.getMarker("PAYU_RENEWAL_STATUS_ERROR");
    public static final Marker PAYU_CHARGING_FAILURE = MarkerFactory.getMarker("PAYU_CHARGING_FAILURE");
    public static final Marker INVALID_URI_SYNTAX = MarkerFactory.getMarker("INVALID_URI_SYNTAX");
    public static final Marker HTTP_ERROR = MarkerFactory.getMarker("HTTP_ERROR");
    public static final Marker PAYTM_ERROR = MarkerFactory.getMarker("PAYTM_ERROR");
    public static final Marker APB_ERROR = MarkerFactory.getMarker("APB_ERROR");
    public static final Marker RECURRING_PAYMENT_EVENT = MarkerFactory.getMarker("RECURRING_PAYMENT_EVENT");
}
