package in.wynk.payment.core.constant;

import in.wynk.common.constant.BaseConstants;

public interface PaymentConstants extends BaseConstants {

    String CARD = "CARD";
    String TXN_ID = "tid";
    String ERROR = "error";
    String SUBSID = "subsId";
    String STATUS = "status";
    String FAILED = "failed";
    String FAILURE = "failure";
    String RENEWAL = "renewal";
    String REQUEST = "request";
    String PENDING = "pending";
    String QUEUED = "queued";
    String SUCCESS = "success";
    String PIPE_SEPARATOR = "|";
    String BASE_USER_EMAIL = "@wynk.in";

    String PAYMENT_GATEWAY = "paymentGateway";

    String MESSAGE = "message";
    String PAYMENT_METHOD = "paymentMethod";
    String MIGRATED_TXN_ID = "transactionid";
    String REQUEST_PAYLOAD = "requestPayload";
    
    String SOURCE_MODE = "mode";
    String MERCHANT_TRANSACTION = "merchantTransaction";

    String ATTEMPT_SEQUENCE = "attemptSequence";
    String PAYMENT_ERROR_UPSERT_RETRY_KEY = "paymentErrorUpsertRetry";
    String PAYMENT_CLIENT_CALLBACK_RETRY = "paymentClientCallbackRetry";
    String MERCHANT_TRANSACTION_UPSERT_RETRY_KEY = "merchantTransactionUpsertRetry";

}
