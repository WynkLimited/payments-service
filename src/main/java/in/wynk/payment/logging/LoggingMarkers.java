package in.wynk.payment.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class LoggingMarkers {
    public static final Marker HTTP_ERROR = MarkerFactory.getMarker("HTTP_ERROR");
    public static final Marker APPLICATION_ERROR = MarkerFactory.getMarker("APPLICATION_ERROR");
}
