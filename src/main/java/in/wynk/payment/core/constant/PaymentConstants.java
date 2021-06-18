package in.wynk.payment.core.constant;

import in.wynk.common.constant.BaseConstants;

import java.util.HashMap;
import java.util.Map;

public interface PaymentConstants extends BaseConstants {

    String CARD = "CARD";
    String TXN_ID = "tid";
    String ERROR = "error";
    String WALLET = "WALLET";
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
    String PAYMENT_CODE = "paymentCode";
    String MIGRATED_TXN_ID = "transactionid";
    String REQUEST_PAYLOAD = "requestPayload";
    String WALLET_USER_ID = "walletUserId";
    
    String SOURCE_MODE = "mode";
    String MERCHANT_TRANSACTION = "merchantTransaction";
    String PAYMENT_MODE="paymentMode";
    String ATTEMPT_SEQUENCE = "attemptSequence";
    String PAYMENT_ERROR_UPSERT_RETRY_KEY = "paymentErrorUpsertRetry";
    String PAYMENT_CLIENT_CALLBACK_RETRY = "paymentClientCallbackRetry";
    String MERCHANT_TRANSACTION_UPSERT_RETRY_KEY = "merchantTransactionUpsertRetry";

    String SUBTITLE_TEXT = "subtitle";
    String BUTTON_TEXT = "buttonText";
    String BUTTON_ARROW = "buttonArrow";

    Map<String,String> FAIL001_ERROR_MAP = new HashMap<String, String>() {{
        put(SUBTITLE_TEXT, "We could not process your payment");
        put(BUTTON_TEXT, "TRY ANOTHER OPTION");
        put(BUTTON_ARROW,"true");
    }};

    Map<String,String> FAIL002_ERROR_MAP = new HashMap<String, String>() {{
        put(SUBTITLE_TEXT, "We are still processing your payment");
        put(BUTTON_TEXT, "GO TO HOMEPAGE");
        put(BUTTON_ARROW,"false");
    }};

}
