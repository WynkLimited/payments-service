package in.wynk.payment.dto.aps.common;

public interface ApsConstant {
    String SIGNATURE = "signature";
    String WYNK_LIMITED = "Wynk Limited";
    String CHANNEL_ID = "channel-id";
    String AUTH_TYPE_WEB_UNAUTH = "WEB_UNAUTH";
    String CONTENT_TYPE = "Content-Type";
    String APPLICATION_JSON = "application/json";
    String ORDER_ID = "orderId";
    String AIRTEL_PAY_STACK = "aps";
    String APS = "APS";
    String PAYMENT_TIMER_KEY = "payment_timer";
    String PAYMENT_STATUS_POLL_KEY = "payment_status_poll";
    String FIELD_TYPE = "type";
    Integer DEFAULT_CIRCLE_ID = -1;
    String PG_STATUS_PENDING = "PG_PENDING";
    String PG_STATUS_SUCCESS = "PG_SUCCESS";
    String PG_STATUS_FAILED = "PG_FAILED";
    String TXN_SUCCESS = "TXN_SUCCESS";
    String PRE_DEBIT_SI= "pre_debit_SI";
    String LOB_AUTO_PAY_REGISTER_WYNK = "AUTO_PAY_REGISTER_WYNK";
    String LOB_SI_WYNK = "SI_WYNK";
    String APS_CARD_TYPE = "cardType";
}
