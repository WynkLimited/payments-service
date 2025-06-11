package in.wynk.payment.constant;

import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public interface PaymentLoggingMarker extends BaseLoggingMarkers {
    Marker MERCHANT_TXN_EVENT_ERROR = MarkerFactory.getMarker("MERCHANT_TXN_EVENT_ERROR");
}
