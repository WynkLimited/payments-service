package in.wynk.payment.utill;

import org.apache.commons.codec.binary.Base64;

public class Utils {

    public static String encodeBase64(String key) {
        return Base64.encodeBase64String(key.getBytes());
    }
}
