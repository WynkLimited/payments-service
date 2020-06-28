package in.wynk.payment.logging;

import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class PaymentLoggingMarkers extends BaseLoggingMarkers {
    public static final Marker HTTP_ERROR = MarkerFactory.getMarker("HTTP_ERROR");
    public static final Marker PAYTM_ERROR = MarkerFactory.getMarker("PAYTM_ERROR");
}
