package in.wynk.payment.logging;

import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class LoggingMarkers extends BaseLoggingMarkers {
    public static final Marker HTTP_ERROR = MarkerFactory.getMarker("HTTP_ERROR");
}
