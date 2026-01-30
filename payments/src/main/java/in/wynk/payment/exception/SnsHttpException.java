package in.wynk.payment.exception;

import com.amazonaws.SdkBaseException;
import com.amazonaws.util.IOUtils;
import org.apache.http.HttpResponse;

import java.io.IOException;

public final class SnsHttpException extends SdkBaseException {
    private final int statusCode;

    public SnsHttpException(String message, HttpResponse response) {
        super(String.format("%s: %d %s.%n%s", message, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(), trySlurpContent(response)));
        this.statusCode = response.getStatusLine().getStatusCode();
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    private static String trySlurpContent(HttpResponse response) {
        try {
            return IOUtils.toString(response.getEntity().getContent());
        } catch (IOException var2) {
            return "";
        }
    }
}