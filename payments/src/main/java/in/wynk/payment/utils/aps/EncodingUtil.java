package in.wynk.payment.utils.aps;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncodingUtil {

    public static String base64Encode(String plainString) {
        return base64Encode(plainString.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Encode(byte[] plainBytes) {
        return Base64.getUrlEncoder().encodeToString(plainBytes);
    }

    public static byte[] base64Decode(String encodedString) {
        return Base64.getUrlDecoder().decode(encodedString);
    }

    public static byte[] base64Decode(byte[] encodedBytes) {
        return Base64.getUrlDecoder().decode(encodedBytes);
    }
}
