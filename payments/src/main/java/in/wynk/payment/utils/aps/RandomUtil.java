package in.wynk.payment.utils.aps;

import java.util.stream.IntStream;

public class RandomUtil {
    private static final char[] SELECTED_CHARS = new char[]{'!', '#', '$', '%', '&', '(', ')', '*', '+', '-', '.', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?', '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    public static String generateRandomString(int length) {
        StringBuffer sb = new StringBuffer();
        IntStream.range(0, length).forEach((i) -> {
            sb.append(SELECTED_CHARS[(int)(Math.random() * 997.0) % SELECTED_CHARS.length]);
        });
        return sb.toString();
    }

    private RandomUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
