package in.wynk.payment.core.constant;

public interface PaymentConstants {

    String COLON = ":";
    String TXN_ID = "tid";
    String ERROR = "error";
    String SUBSID = "subsId";
    String STATUS = "status";
    String SESSION_ID = "sid";
    String REQUEST = "request";
    String PENDING = "pending";
    String SUCCESS = "success";
    String FAILURE = "failure";
    String PIPE_SEPARATOR = "|";
    String BASE_USER_EMAIL = "@wynk.in";


    String PAYMENT_METHOD = "paymentMethod";
    String REQUEST_PAYLOAD = "requestPayload";

    String PAYMENT_ERROR_UPSERT_RETRY_KEY = "paymentErrorUpsertRetry";
    String MERCHANT_TRANSACTION_UPSERT_RETRY_KEY = "merchantTransactionUpsertRetry";


}
