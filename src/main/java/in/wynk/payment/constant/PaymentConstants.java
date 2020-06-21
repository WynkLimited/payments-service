package in.wynk.payment.constant;

public class PaymentConstants {

    public static final String PAYTM_CHECKSUMHASH                   = "CHECKSUMHASH";
    public static final String PAYTM_CHECKSUM                       = "CHECKSUM";
    public static final String PAYTM_RENEW_ACKNOWLEDGEMENT_STATUS   = "TXN_ACCEPTED";
    public static final String PAYTM_TXNID                          = "TXNID";
    public static final String PAYTM_STATUS                         = "STATUS";
    public static final String PAYTM_BANKTXNID                      = "BANKTXNID";
    public static final String PAYTM_TXNAMOUNT                      = "TXNAMOUNT";
    public static final String PAYTM_CURRENCY                       = "CURRENCY";
    public static final String PAYTM_RESPCODE                       = "RESPCODE";
    public static final String PAYTM_RESPMSG                        = "RESPMSG";
    public static final String PAYTM_TXNDATE                        = "TXNDATE";
    public static final String PAYTM_GATEWAYNAME                    = "GATEWAYNAME";
    public static final String PAYTM_BANKNAME                       = "BANKNAME";
    public static final String PAYTM_PAYMENTMODE                    = "PAYMENTMODE";
    public static final String PAYTM_RENEW_REQUEST_PENDING_CODE     = "917";
    public static final String ADD_MONEY                            = "ADD_MONEY";
    public static final String RETAIL                               = "Retail";
    public static final String PAYTM_REQUEST_TYPE                   = "REQUEST_TYPE";
    public static final String PAYTM_MID                            = "MID";
    public static final String PAYTM_REQUST_ORDER_ID                = "ORDER_ID";
    public static final String PAYTM_REQUST_ORDERID                 = "ORDERID";
    public static final String PAYTM_REQUEST_SUBS_ID                = "SUBS_ID";
    public static final String PAYTM_REQUEST_TXN_AMOUNT             = "TXN_AMOUNT";
    public static final String PAYTM_THEME                          = "THEME";
    public static final String PAYTM_REQUEST_CUST_ID                = "CUST_ID";
    public static final String PAYTM_LOGIN_THEME                    = "LOGIN_THEME";
    public static final String PAYTM_CHANNEL_ID                     = "CHANNEL_ID";
    public static final String PAYTM_INDUSTRY_TYPE_ID               = "INDUSTRY_TYPE_ID";
    public static final String PAYTM_WEBSITE                        = "WEBSITE";
    public static final String PAYTM_WEB                            = "WAP";
    public static final String PAYTM_SUBS_SERVICE_ID                = "SUBS_SERVICE_ID";
    public static final String PAYTM_ORDER_DETAILS                  = "ORDER_DETAILS";
    public static final String PAYTM_SUBS_AMOUNT_TYPE               = "SUBS_AMOUNT_TYPE";
    public static final String PAYTM_SUBS_MAX_AMOUNT                = "SUBS_MAX_AMOUNT";
    public static final String PAYTM_SUBS_FREQUENCY                 = "SUBS_FREQUENCY";
    public static final String PAYTM_SUBS_FREQUENCY_UNIT            = "SUBS_FREQUENCY_UNIT";
    public static final String PAYTM_SUBS_ENABLE_RETRY              = "SUBS_ENABLE_RETRY";
    public static final String PAYTM_SUBS_EXPIRY_DATE               = "SUBS_EXPIRY_DATE";
    public static final String PAYTM_RESPONSE_ORDERID               = "ORDERID";
    public static final String PAYTM_STATUS_FAILURE                 = "TXN_FAILURE";
    public static final String PAYTM_STATUS_SUCCESS                 = "TXN_SUCCESS";
    public static final String PAYTM_STATUS_ACCEPTED                = "TXN_ACCEPTED";
    public static final String PAYTM_STATUS_PENDING                 = "TXN_PENDING";
    public static final String PAYTM_REFUNDAMOUNT                   = "REFUNDAMOUNT";
    public static final String PAYTM_TXNTYPE                        = "TXNTYPE";
    public static final String PAYTM_COMMENTS                       = "COMMENTS";
    public static final String PAYTM_REFID                          = "REFID";
    public static final String PAYTM_SUBS_ENABLE_WITHOUT_SAVED_CARD = "SUBS_PPI_ONLY";
    public static final String PAYTM_SSO_TOKEN                      = "SSO_TOKEN";
    public static final String PAYTM_REQUEST_CALLBACK               = "CALLBACK_URL";

    /*** Constants for PAYTM GRATIFICATION API CONSTANTS ***/
    public interface PAYTM_GRATIFICATION {
        String PAYTM_REQUEST_TYPE = "requestType";
        String PAYTM_MERCHANT_GUID = "merchantGuid";
        String PAYTM_MERCHANT_ORDER_ID = "merchantOrderId";
        String PAYTM_SALES_WALLET_NAME = "salesWalletName";
        String PAYTM_SALES_WALLET_GUID = "salesWalletGuid";
        String PAYTM_PAYEE_EMAIL = "payeeEmailId";
        String PAYTM_PAYEE_PHONE = "payeePhoneNumber";
        String PAYTM_PAYEE_SSOID = "payeeSsoId";
        String PAYTM_APPLIED_TO_NEW_USERS = "appliedToNewUsers";
        String PAYTM_AMOUNT = "amount";
        String PAYTM_CURRENCY_CODE = "currencyCode";
        String PAYTM_SALES_TO_USER_CREDIT = "SALES_TO_USER_CREDIT";
        String PAYTM_CHECK_TXN_STATUS = "CHECK_TXN_STATUS";
        String PAYTM_TXN_TYPE = "txnType";
        String PAYTM_TXN_ID = "txnId";
    }
}
