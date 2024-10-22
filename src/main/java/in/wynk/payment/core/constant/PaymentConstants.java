package in.wynk.payment.core.constant;

import in.wynk.common.constant.BaseConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface PaymentConstants extends BaseConstants {

    String SKU_ID = "skuId";
    String SHOW_GPB = "showGPB";
    String SAVED = "SAVED";
    String PAYU = "PAYU";
    String TXN_ID = "tid";
    String ERROR = "error";
    String VERSION_2 = "V2";
    String FAILED = "failed";
    String QUEUED = "queued";
    String PROD_ENV = "PROD";
    String ITUNES = "ITUNES";
    String MERCHANT_ID = "id";
    String FAILURE = "failure";
    String REQUEST = "request";
    String PENDING = "pending";
    String SUCCESS = "success";
    String MESSAGE = "message";
    String ERROR_REASON = "errorReason";
    String PIPE_SEPARATOR = "|";
    String SOURCE_MODE = "mode";
    String BANK_CODE = "bankCode";
    String SANDBOX_ENV = "Sandbox";
    String MERCHANT_TOKEN = "token";
    String SE_BILLING = "SE_BILLING";
    String AMAZON_IAP = "AMAZON_IAP";
    String GOOGLE_IAP = "GOOGLE_IAP";
    String SUBTITLE_TEXT = "subtitle";
    String BUTTON_TEXT = "buttonText";
    String MERCHANT_SECRET = "secret";
    String APB_GATEWAY = "APB_GATEWAY";
    String DEFAULT_PN = "Wynk Limited";
    String ADD_TO_BILL = "ADD_TO_BILL";
    String APP_PACKAGE = "package_name";
    String APP_NAME = "APP_NAME";
    String BASE_USER_EMAIL = "@wynk.in";
    String PAYMENT_CODE = "paymentCode";
    String PAYMENT_MODE = "paymentMode";
    String BUTTON_ARROW = "buttonArrow";
    String USER_WINBACK = "user_winback";
    String WINBACK_NOTIFICATION_URL = "WINBACK_URL";
    String PLAN_ID_PLACEHOLDER = "<PLAN_ID>";
    String MERCHANT_CLIENT_ID = "clientId";
    String PAYMENT_METHOD = "paymentMethod";
    String TDR = "tdr";
    String SHOULD_WINBACK = "shouldWinBack";
    String MIGRATED_TXN_ID = "transactionid";
    String PAYMENT_API_CLIENT = "paymentApi";
    String PAYMENT_GATEWAY = "paymentGateway";
    String REQUEST_PAYLOAD = "requestPayload";
    String ATTEMPT_SEQUENCE = "attemptSequence";
    String WINBACK_CAMPAIGN = "winback_campaign";
    String PHONEPE_AUTO_DEBIT = "PHONEPE_AUTO_DEBIT";
    String PAY_OPTION_DEEPLINK = "pay_option_deeplink";
    String PAYMENT_DETAILS_KEY = "PAYMENT_DETAILS_KEY:";
    String MERCHANT_TRANSACTION = "merchantTransaction";
    String PAYMENT_PAGE_PLACE_HOLDER = "${payment.%c.%p.page}";
    String PAYMENT_PAGE_PLACE_HOLDER_V2 = "${payment.%c.%p.v2.page}";
    String ORIGINAL_TRANSACTION_ID = "ORIGINAL_TRANSACTION_ID";
    String PAYMENT_DROPOUT_TRACKER_IN_SECONDS = "PAYMENT_DROPOUT_TRACKER_IN_SECONDS";
    String PAYMENT_ENTITY_BASE_PACKAGE = "in.wynk.payment.core.dao";
    String PAYMENT_CLIENT_PLACE_HOLDER = "payment.merchant.%p.%c.%f";
    String PAYMENT_CLIENT_PLACE_HOLDER2 = "payment.merchant.%p.%c.%r.%f";
    String PAYMENT_ERROR_UPSERT_RETRY_KEY = "paymentErrorUpsertRetry";
    String PAYMENT_CLIENT_CALLBACK_RETRY = "paymentClientCallbackRetry";
    String MERCHANT_TRANSACTION_UPSERT_RETRY_KEY = "merchantTransactionUpsertRetry";
    String INVOICE_RETRY = "invoiceRetry";
    String INVOICE_SEQUENCE_LOCK_KEY = "invoiceSequenceLockKey";
    String INVOICE_SEQUENCE_PREFIX = "ADL";
    String INFORM_INVOICE_MESSAGE = "informInvoiceMessage";
    String TAXABLE_REQUEST = "taxableRequest";
    String TAXABLE_RESPONSE = "taxableResponse";
    String OPTIMUS_GST_STATE_CODE = "optimusGSTStateCode";
    String GEOLOCATION_GST_STATE_CODE = "geoLocationGSTStateCode";
    String DEFAULT_GST_STATE_CODE = "defaultGSTStateCode";
    String ACCESS_STATE_CODE = "accessStateCode";
    String INVOICE_CATEGORY = "ONLINE CONTENT SERVICES";
    String BLANK = "";
    String PAYMENT_CLIENT_AUTHORIZATION = "authentication.details.getApiServices().contains(\"payment\")";

    List<String> IAP_PAYMENT_METHODS = Arrays.asList(PaymentConstants.ITUNES, PaymentConstants.AMAZON_IAP, PaymentConstants.GOOGLE_IAP);

    String CONTENT_TYPE = "Content-Type";
    String CURRENCY_INR = "INR";
    String WHATSAPP = "WHATSAPP";
    String UNKNOWN_VPA = "UNKNOWN_VPA";
    String ANDROID_DEEP_LINK = "android_deep_link";
    String DESKTOP_DEEP_LINK = "desktop_deep_link";
    String IOS_DEEP_LINK = "ios_deep_link";
    String FALLBACK_URL = "fallback_url";
    String AIRTEL_XSTREAM = "airtelxstream";
    String AIRTEL_TV = "airteltv";
    Double MANDATE_FLOW_AMOUNT = 1.0;
    String PAYMENT_FLOW_MANDATE = "MANDATE";
    String MANDATE_REVOKE_RESPONSE = "MANDATE_REVOKE_RESPONSE";
    String PAYMENT_FLOW_TRIAL_OPTED = "TRIALOPTED";
    String PAYMENT_FLOW_AUTO_RENEW = "AUTORENEW";
    String ADD_TO_BILL_USER_SUBSCRIPTION_STATUS_TASK = "AddToBillUserSubscriptionStatusTask";
    String EXTERNAL_TRANSACTION_TOKEN = "externalTransactionToken";
    List<Integer> MUSIC_PLAN_IDS = Arrays.asList(2102, 1103, 11002, 1107, 13011, 110011, 110012, 110021, 110022, 110041, 110042, 1100072, 1100073, 1100074, 1100075, 1100076, 110051, 1105, 11016, 11019, 11041, 11001, 1106, 111001, 1125, 1115, 13013, 2201, 2202, 2203, 2204, 2205, 2206, 8100357);
    List<Integer> MUSIC_PLAN_IDS_OF_REFUND = Arrays.asList(2107, 11007, 11018, 11021, 13001, 21002, 21041, 1053, 2102, 12005, 130051);
    String E6002 = "E6002";
    String RENEWALS_INELIGIBLE_PLANS = "renewalsIneligiblePlans";
    String ERROR_DESCRIPTION_FOR_E6002 = "Invalid vpa ! please try again";
    String DESCRIPTION = "description";

    String REFUND_TEMPLATE_ID= "refund_template_id";

    String TRANSACTION = "transaction";
    int MAXIMUM_RENEWAL_RETRY_ALLOWED = 3;
    List<Integer> excludedAmounts = Arrays.asList(1, 49, 59, 69);
    ArrayList<String> ERROR_REASONS = new ArrayList<>(
            Arrays.asList("mandate status is: revoked", "|Mandate has been revoked", "mandate status is: pause", "Card has been classified as lost and has been blocked.",
                    "|Execution Request Already Initiated",
                    "Mandated SI transactions, consent transaction is not enrolled for SI",
                    "PDN Failed. Mandate is not Active", "Mandate is in FAILURE state. Recurring cannot be executed", "Mandate is in CREATED state. Recurring cannot be executed",
                    "Mandate is in REVOKED state. Recurring cannot be executed", "PDN Failed. Unable to process the request with PG. Please try again", "Mandate is already pause",
                    "Mandate is already revoked", "PDN Failed. Consent is Not Enrolled Or Not IN Active State", "PDN Failed. Gateway not supported."));

}