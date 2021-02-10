package in.wynk.payment.core.constant;

import in.wynk.common.constant.BaseConstants;

public interface PaymentConstants extends BaseConstants {

    String TXN_ID = "tid";
    String MIGRATED_TXN_ID = "transactionid";
    String ERROR = "error";
    String SUBSID = "subsId";
    String STATUS = "status";
    String FAILURE = "failed";
    String RENEWAL = "renewal";
    String REQUEST = "request";
    String PENDING = "pending";
    String SUCCESS = "success";
    String PIPE_SEPARATOR = "|";
    String BASE_USER_EMAIL = "@wynk.in";

    String PAYMENT_METHOD = "paymentMethod";
    String REQUEST_PAYLOAD = "requestPayload";

    String SOURCE_MODE = "mode";

    String PAYMENT_ERROR_UPSERT_RETRY_KEY = "paymentErrorUpsertRetry";
    String PAYMENT_CLIENT_CALLBACK_RETRY = "paymentClientCallbackRetry";
    String MERCHANT_TRANSACTION_UPSERT_RETRY_KEY = "merchantTransactionUpsertRetry";


}
