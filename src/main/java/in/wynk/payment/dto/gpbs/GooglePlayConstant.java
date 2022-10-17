package in.wynk.payment.dto.gpbs;

import com.google.common.base.Predicates;

import java.util.Arrays;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
public class GooglePlayConstant {

    public static final String API_KEY_PARAM = "?key=";
    public static final String AUTH_TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN = "/tokens/";
    public static final String ACKNOWLEDGE = ":acknowledge";

    public static final Integer PURCHASE_NOTIFICATION_TYPE = 4;
    public static final String GOOGLE_PLAY_RECEIPT = "googlePlayReceipt";

    public static final String SERVICE_MUSIC = "music";
    public static final String SERVICE_RAJ_TV = "rajtv";
    public static final String SERVICE_AIRTEL_TV = "airteltv";

    public static String MUSIC_PACKAGE_NAME = "com.bsbportal.music";
    public static String AIRTEL_TV_PACKAGE_NAME = "tv.accedo.airtel.wynk";
    public static String RAJ_TV_PACKAGE_NAME = "com.rajdigital.tv";
    //New payment group
    public static final String BILLING = "BILLING";

    public static final List<String> NOTIFICATIONS_TYPE_ALLOWED =
            Arrays.asList(" SUBSCRIPTION_RECOVERED", "SUBSCRIPTION_RENEWED", "SUBSCRIPTION_CANCELED", "SUBSCRIPTION_ON_HOLD", "SUBSCRIPTION_IN_GRACE_PERIOD",
                    "SUBSCRIPTION_RESTARTED", "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED", "SUBSCRIPTION_DEFERRED", "SUBSCRIPTION_PAUSED", "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED", "SUBSCRIPTION_REVOKED",
                    "SUBSCRIPTION_EXPIRED");
}
