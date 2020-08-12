package in.wynk.payment.test.utils;

import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.PlanPeriodDTO;
import in.wynk.commons.dto.PriceDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PaymentGroup;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.State;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Card;
import in.wynk.payment.core.dao.entity.Payment;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static in.wynk.commons.constants.BaseConstants.MSISDN;
import static in.wynk.commons.constants.BaseConstants.SERVICE;
import static in.wynk.commons.constants.BaseConstants.UID;

public class PaymentTestUtils {

    public static final String DUMMY_UID = "lViUuniOH80osYFqy0";
    public static final int PLAN_ID = 1000180;
    private static final String DUMMY_MSISDN = "1111111111";

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
        map.put(MSISDN, DUMMY_MSISDN);
        map.put(UID, DUMMY_UID);
        map.put(SERVICE, "airteltv");
        SessionDTO sessionDTO = new SessionDTO();
        sessionDTO.setSessionPayload(map);
        return sessionDTO;
    }

    public static List<PlanDTO> dummyPlansDTO() {
        return Collections.singletonList(dummyPlanDTO());
    }

    public static PlanDTO dummyPlanDTO() {
        PlanPeriodDTO planPeriodDTO = PlanPeriodDTO.builder().timeUnit(TimeUnit.DAYS).validity(30).build();
        PriceDTO priceDTO = PriceDTO.builder().amount(50).currency("INR").build();
        return PlanDTO.builder().planType(PlanType.ONE_TIME_SUBSCRIPTION).id(PLAN_ID).period(planPeriodDTO).price(priceDTO).title("DUMMY_PLAN").build();
    }

    public static SessionDTO dummyAtvSession() {
        Map<String, Object> map = new HashMap<>();
        map.put(MSISDN, DUMMY_MSISDN);
        map.put(UID, DUMMY_UID);
        map.put(SERVICE, "airteltv");
        SessionDTO sessionDTO = new SessionDTO();
        sessionDTO.setSessionPayload(map);
        return sessionDTO;
    }
}
