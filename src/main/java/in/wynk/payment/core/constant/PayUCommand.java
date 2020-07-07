package in.wynk.payment.core.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayUCommand {

    VERIFY_PAYMENT("verify_payment"),
    CARD_BIN_INFO("check_isDomestic"),
    USER_CARD_DETAILS("get_user_cards"),
    SI_TRANSACTION("si_transaction");


    private final String code;

}
