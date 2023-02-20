package in.wynk.payment.dto.apb;

public interface ApbConstants {
    String STATUS = "STATUS";
    String MID = "MID";
    String TRAN_ID = "TRAN_ID";
    String TRAN_AMT = "TRAN_AMT";
    String TRAN_CUR = "TRAN_CUR";
    String TRAN_DATE = "TRAN_DATE";
    String TXN_REF_NO = "TXN_REF_NO";
    String HASH = "HASH";
    String CODE = "CODE";
    String MSG = "MSG";
    String SUCCESS_URL = "SU";
    String FAILURE_URL = "FU";
    String APB_AMOUNT = "AMT";
    String DATE = "DATE";
    String CURRENCY = "CUR";
    String CUSTOMER_MOBILE = "CUST_MOBILE";
    String MERCHANT_NAME = "MNAME";
    String ECOMM_INQ = "ECOMM_INQ";
    String LANG_ID = "001";
    String POSTPAID = "POSTPAID";
    //header data
    String CONTENT_TYPE ="Content-Type";
    String APPLICATION_JSON= "application/json";
    String CHANNEL_ID="channel-id";
    //UPI Intent data
    String PA= "pa";
    String PN= "pn";
    String TR= "tr";
    String AM= "am";
    String CU= "cu";
    String TN= "tn";
    String WYNK_LIMITED= "Wynk Limited";
    String APS_VERIFY_TRANSACTION_SUCCESS="TXN_SUCCESS";
    String AUTH_TYPE_WEB_UNAUTH="WEB_UNAUTH";
    String CURRENCY_INR="INR";
    //String WYNK_RECURRING = "WYNK_RECURRING";
    Integer DEFAULT_CIRCLE_ID = -1;
    String PG_STATUS_PENDING = "PG_PENDING";
    String PG_STATUS_SUCCESS = "PG_SUCCESS";
    String PG_STATUS_FAILED = "PG_FAILED";
    String TXN_SUCCESS = "TXN_SUCCESS";
    String FRESH_CARD_TYPE = "FRESH";
    String SAVED_CARD_TYPE = "SAVED";
    String IV_USER= "iv-user";
}
