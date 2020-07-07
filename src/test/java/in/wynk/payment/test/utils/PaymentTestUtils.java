package in.wynk.payment.test.utils;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PaymentGroup;
import in.wynk.commons.enums.State;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Card;
import in.wynk.payment.core.dao.entity.Payment;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;

import java.util.HashMap;
import java.util.Map;

import static in.wynk.commons.constants.Constants.UID;

public class PaymentTestUtils {

    public static final String DUMMY_UID = "test_uid";

    public static PaymentMethod dummyNetbankingMethod() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("icon_url", "/wp-content/themes/");
        meta.put("promo_msg", "Save and Pay via Cards.");
        meta.put("disable_message", "");
        return new PaymentMethod.Builder().displayName("Credit / Debit Cards").group(PaymentGroup.NET_BANKING).hierarchy(10)
                .meta(meta).paymentCode(PaymentCode.PAYU).state(State.ACTIVE).build();
    }

    public static PaymentMethod dummyCardMethod() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("icon_url", "/wp-content/themes/");
        meta.put("promo_msg", "Save and Pay via Cards.");
        meta.put("disable_message", "");
        return new PaymentMethod.Builder().displayName("Credit / Debit Cards").group(PaymentGroup.CARD).hierarchy(10)
                .meta(meta).paymentCode(PaymentCode.PAYU).state(State.ACTIVE).build();
    }

    public static PaymentMethod dummyWalletMethod() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("icon_url", "/wp-content/themes/");
        meta.put("promo_msg", "Save and Pay via Cards.");
        meta.put("disable_message", "");
        return new PaymentMethod.Builder().displayName("Credit / Debit Cards").group(PaymentGroup.WALLET).hierarchy(10)
                .meta(meta).paymentCode(PaymentCode.PAYTM_WALLET).state(State.ACTIVE).build();
    }

    public static UserPreferredPayment dummyPreferredWallet() {
        Payment payment = new Wallet.Builder().paymentCode(PaymentCode.PAYTM_WALLET).build();
        return new UserPreferredPayment.Builder().option(payment).uid(DUMMY_UID).build();
    }

    public static UserPreferredPayment dummyPreferredCard() {
        Payment payment = new Card.Builder().paymentCode(PaymentCode.PAYU).build();
        return new UserPreferredPayment.Builder().option(payment).uid(DUMMY_UID).build();
    }

    public static SessionDTO dummySession() {
        Map<String, Object> map = new HashMap<>();
        map.put(UID, DUMMY_UID);
        SessionDTO sessionDTO = SessionDTO.builder().build();
        sessionDTO.setPayload(map);
        return sessionDTO;
    }
}
