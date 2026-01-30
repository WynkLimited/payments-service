package in.wynk.payment.exception;

import in.wynk.exception.IWynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import org.slf4j.Marker;

public class PaymentRuntimeException extends WynkRuntimeException {

    public PaymentRuntimeException(Marker marker, String message, Throwable th) {
        super(marker, message, th);
    }

    public PaymentRuntimeException(IWynkErrorType wynkErrorType) {
        super(wynkErrorType);
    }

    public PaymentRuntimeException(IWynkErrorType wynkErrorType, Marker marker) {
        super(wynkErrorType, marker);
    }

    public PaymentRuntimeException(IWynkErrorType wynkErrorType, String message) {
        super(wynkErrorType, message);
    }

    public PaymentRuntimeException(IWynkErrorType wynkErrorType, Throwable th, String message) {
        super(wynkErrorType, th, message);
    }

    public PaymentRuntimeException(IWynkErrorType wynkErrorType, Throwable th) {
        super(wynkErrorType, th);
    }

    public PaymentRuntimeException(String message) {
        super(message);
    }

    public PaymentRuntimeException(String errorCode, String message, String errorTitle, Throwable th) {
        super(errorCode, message, errorTitle, th);
    }

    public PaymentRuntimeException(String errorCode, String message, String errorTitle) {
        super(errorCode, message, errorTitle);
    }

    public PaymentRuntimeException(Throwable th) {
        super(th);
    }

    public PaymentRuntimeException(String errorMsg, Throwable th) {
        super(errorMsg, th);
    }
}
