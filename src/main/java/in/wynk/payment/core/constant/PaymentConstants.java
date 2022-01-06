package in.wynk.payment.core.constant;

import in.wynk.common.constant.BaseConstants;

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
    String PAYMENT_DETAILS_KEY = "PAYMENT_DETAILS_KEY:";

    String PAYMENT_GATEWAY = "paymentGateway";

    String MESSAGE = "message";
    String PAYMENT_METHOD = "paymentMethod";
    String PAYMENT_CODE = "paymentCode";
    String MIGRATED_TXN_ID = "transactionid";
    String REQUEST_PAYLOAD = "requestPayload";
    String WALLET_USER_ID = "walletUserId";

    String SOURCE_MODE = "mode";
    String BANK_CODE = "bankCode";
    String MERCHANT_TRANSACTION = "merchantTransaction";
    String PAYMENT_MODE = "paymentMode";
    String ATTEMPT_SEQUENCE = "attemptSequence";
    String ORIGINAL_TRANSACTION_ID = "ORIGINAL_TRANSACTION_ID";
    String PAYMENT_ERROR_UPSERT_RETRY_KEY = "paymentErrorUpsertRetry";
    String PAYMENT_CLIENT_CALLBACK_RETRY = "paymentClientCallbackRetry";
    String MERCHANT_TRANSACTION_UPSERT_RETRY_KEY = "merchantTransactionUpsertRetry";

    String SUBTITLE_TEXT = "subtitle";
    String BUTTON_TEXT = "buttonText";
    String BUTTON_ARROW = "buttonArrow";

    String PROD_ENV = "PROD";
    String SANDBOX_ENV = "Sandbox";
    String USER_WINBACK = "user_winback";
    String PAY_OPTION_DEEPLINK = "pay_option_deeplink";
    String SHOULD_WINBACK = "shouldWinBack";
    String WINBACK_CAMPAIGN = "winback_campaign";
    String PAYMENT_DROPOUT_DELAY_KEY = "transactionTrackerDelaySec";
    String PAYMENT_ENTITY_BASE_PACKAGE = "in.wynk.payment.core.dao";
    String DEFAULT_COUNTRY_CODE = "IN";
    String PAYMENT_CLIENT_AUTHORIZATION = "authentication.details.getApiServices().contains(\"payment\")";
    String PAYMENT_CLIENT_PLACE_HOLDER = "payment.merchant.%p.%c.%f";
    String MERCHANT_ID = "id";
    String MERCHANT_SECRET = "secret";
    String MERCHANT_TOKEN = "token";
    String MERCHANT_CLIENT_ID = "clientId";
    String PAYMENT_API_CLIENT = "paymentApi";

}