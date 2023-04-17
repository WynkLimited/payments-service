package in.wynk.payment.dto.aps.common;

public interface ApsConstant {
    String SIGNATURE = "signature";
    String APS_LOB_AUTO_PAY_REGISTER_WYNK = "AUTO_PAY_REGISTER_WYNK";
    String APS_LOB_WYNK = "WYNK";
    String LOB_SI_WYNK = "SI_WYNK";

    String CURRENCY_INR="INR";
    Integer DEFAULT_CIRCLE_ID = -1;
    String PG_STATUS_PENDING = "PG_PENDING";
    String PG_STATUS_SUCCESS = "PG_SUCCESS";
    String PG_STATUS_FAILED = "PG_FAILED";
    String TXN_SUCCESS = "TXN_SUCCESS";
    String PRE_DEBIT_SI= "pre_debit_SI";
}
