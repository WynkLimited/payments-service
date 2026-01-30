package in.wynk.payment.dto.payu;


import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;

public interface PayUConstants extends PaymentConstants {

    String GENERIC_CALLBACK_ACTION = "generic";
    String PAYMENT_STATUS = "PAYMENT_STATUS";
    String REFUND_CALLBACK_ACTION = "REFUND_STATUS";
    String REALTIME_MANDATE_CALLBACK_ACTION = "MANDATE_STATUS";
    String PAYU_PG = "pg";
    String PAYU_SI = "SI";
    String PAYU_VPA = "vpa";
    String PAYU_SI_KEY = "si";
    String PAYU_HASH = "hash";
    String PAYU_VARIABLE = "var";
    String PAYU_VARIABLE1 = "var1";
    String PAYU_REQUEST_TYPE = "rt";
    String PAYU_COMMAND = "command";
    String PAYU_SUCCESS_URL = "surl";
    String PAYU_FAILURE_URL = "furl";
    String PAYU_MERCHANT_KEY = "key";
    String PAYU_BANKCODE = "bankcode";
    String PAYU_CARD_TYPE = "cardType";
    String PAYU_UDF1_PARAMETER = "udf1";
    String PAYU_CUSTOMER_EMAIL = "email";
    String PAYU_REQUEST_ID = "requestId";
    String PAYU_DEBIT_DATE = "debitDate";
    String PAYU_CARD_TOKEN = "card_token";
    String PAYU_CUSTOMER_MSISDN = "phone";
    String PAYU_FREE_TRIAL = "free_trial";
    String PAYU_SI_DETAILS = "si_details";
    String PAYU_API_VERSION = "api_version";
    String PAYU_PRODUCT_INFO = "productinfo";
    String PAYU_PAYMENT_SOURCE_SIST = "sist";
    String PAYU_TRANSACTION_AMOUNT = "amount";
    String PAYU_TXN_S2S_FLOW = "txn_s2s_flow";
    String PAYU_TXN_S2S_FLOW_VALUE = "4";
    String PAYU_STATUS_NOT_FOUND = "Not Found";
    String PAYU_CUSTOMER_FIRSTNAME = "firstname";
    String PAYU_REQUEST_TRANSACTION_ID = "txnid";
    String PAYU_RESPONSE_AUTH_PAYUID = "authPayuId";
    String PAYU_RESPONSE_AUTH_PAYUID_SMALL = "authpayuid";
    String PAYU_ENFORCE_PAYMENT = "enforce_payment";
    String PAYU_USER_CREDENTIALS = "user_credentials";
    String PAYU_ENFORCE_PAY_METHOD = "enforce_paymethod";
    String PAYU_REDIRECT_MESSAGE = "Redirecting to PayU ";
    String PAYU_IS_FALLBACK_ATTEMPT = "isFallbackAttempt";
    String PAYU_INVOICE_DISPLAY_NUMBER = "invoiceDisplayNumber";
    String PAYU_MERCHANT_CODE = "5815";
    String PAYU_STORE_CARD = "store_card";
    String PAYU_CARD_EXP_MON = "ccexpmon";
    String PAYU_CARD_EXP_YEAR = "ccexpyr";
    String PAYU_CARD_NUM = "ccnum";
    String PAYU_CARD_HOLDER_NAME = "ccname";
    String PAYU_CARD_CVV = "ccvv";
    String PAYU_PG_NET_BANKING_VALUE = "NB";
    String AUTO_REFUND = "autoRefund";
    String INTEGER_VALUE = String.valueOf(1);
}