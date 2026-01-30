package in.wynk.payment.dto.payu;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayUCommand {

    VERIFY_VPA("validateVPA"),
    CARD_BIN_INFO("getBinInfo"),
    PRE_DEBIT_SI("pre_debit_SI"),
    VERIFY_PAYMENT("verify_payment"),
    PAYU_GETTDR("get_TDR"),
    SI_TRANSACTION("si_transaction"),
    USER_CARD_DETAILS("get_user_cards"),
    UPI_MANDATE_STATUS("upi_mandate_status"),
    CHECK_MANDATE_STATUS("check_mandate_status"),
    UPI_MANDATE_REVOKE("upi_mandate_revoke"),
    CHECK_ACTION_STATUS("check_action_status"),
    CANCEL_REFUND_TRANSACTION("cancel_refund_transaction");

    private final String code;

}