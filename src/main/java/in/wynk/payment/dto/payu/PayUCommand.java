package in.wynk.payment.dto.payu;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayUCommand {

    VERIFY_PAYMENT("verify_payment"),
    CARD_BIN_INFO("check_isDomestic"),
    USER_CARD_DETAILS("get_user_cards"),
    SI_TRANSACTION("si_transaction"),
    VERIFY_VPA("validateVPA"),
    CHECK_ACTION_STATUS("check_action_status"),
    CANCEL_REFUND_TRANSACTION("cancel_refund_transaction"),
    UPI_MANDATE_STATUS("upi_mandate_status");


    private final String code;

}
