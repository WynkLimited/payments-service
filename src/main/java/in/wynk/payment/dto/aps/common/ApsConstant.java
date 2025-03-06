package in.wynk.payment.dto.aps.common;

import in.wynk.payment.core.constant.PaymentConstants;

public interface ApsConstant extends PaymentConstants {
    String SIGNATURE = "signature";
    String CHANNEL_ID = "channel-id";
    String AUTH_TYPE_WEB_UNAUTH = "WEB_UNAUTH";
    String CONTENT_TYPE = "Content-Type";
    String APPLICATION_JSON = "application/json";
    String AIRTEL_PAY_STACK = "aps";
    String AIRTEL_PAY_STACK_V2 = "aps_v2";
    String APS = "APS";
    String APS_V2 = "APS_V2";
    String PAYMENT_TIMER_KEY = "payment_timer";
    String PAYMENT_STATUS_POLL_KEY = "payment_status_poll";
    String FIELD_TYPE = "type";
    Integer DEFAULT_CIRCLE_ID = -1;
    String PG_STATUS_PENDING = "PG_PENDING";
    String PG_STATUS_SUCCESS = "PG_SUCCESS";
    String PG_STATUS_FAILED = "PG_FAILED";
    String PRE_DEBIT_SI = "pre_debit_SI";
    String APS_CARD_TYPE = "cardType";
    String PAY_DIGI = "paydigi";
    String INT_PAY = "intpay";
}
