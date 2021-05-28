package in.wynk.payment.core.constant;

import java.util.HashMap;
import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.*;


public class ErrorCodeConstants {
    private static Map<String,String> FAIL001 = new HashMap<String, String>() {{
        put(SUBTITLE_TEXT, "We could not process your payment");
        put(BUTTON_TEXT, "TRY ANOTHER OPTION");
        put(BUTTON_ARROW,"true");
    }};

    private static Map<String,String> FAIL002 = new HashMap<String, String>() {{
        put(SUBTITLE_TEXT, "We are still processing your payment");
        put(BUTTON_TEXT, "GO TO HOMEPAGE");
        put(BUTTON_ARROW,"false");
    }};

    public static Map<String,String> getMapFromErrorCode(String internalCode) {
        switch (internalCode) {
            case "FAIL002":
                return FAIL002;
        }
        return FAIL001;
    }

}
