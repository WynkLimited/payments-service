package in.wynk.payment.dto.gpbs;

import java.util.Arrays;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
public class GooglePlayConstant {

    public static final String API_KEY_PARAM = "?key=";
    public static final String ETERNAL_TRANSACTION_API_KEY_PARAM = "&key=";
    public static final String AUTH_TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN = "/tokens/";
    public static final String ACKNOWLEDGE = ":acknowledge";
    public static final String CONSUME = ":consume";

    public static final Integer PURCHASE_NOTIFICATION_TYPE = 4;
    public static final String GOOGLE_PLAY_RECEIPT = "googlePlayReceipt";

    public static final String SERVICE_MUSIC = "music";
    public static final String SERVICE_RAJ_TV = "rajtv";
    public static final String SERVICE_AIRTEL_TV = "airteltv";
    public static final String SERVICE_ENTERR10 = "enterr10";

    public static String MUSIC_PACKAGE_NAME = "com.bsbportal.music";
    public static String AIRTEL_TV_PACKAGE_NAME = "tv.accedo.airtel.wynk";
    public static String RAJ_TV_PACKAGE_NAME = "com.rajdigital.tv";
    public static String ENTERR10_PACKAGE_NAME = "com.dangalplay.tv";
    //New payment group
    public static final String BILLING = "BILLING";

    public static final List<String> NOTIFICATIONS_TYPE_ALLOWED = Arrays.asList("1" , "2", "3");
    public static final Integer FREE_TRIAL_PAYMENT_STATE = 2;
    public static final String FREE_TRIAL_AMOUNT = "0";
    public static final String GOOGLE_PLAY_ORDER_ID = "orderId";
}
