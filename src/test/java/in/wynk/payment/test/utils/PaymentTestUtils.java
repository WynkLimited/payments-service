package in.wynk.payment.test.utils;

import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.data.enums.State;
import in.wynk.payment.common.messages.PaymentRecurringSchedulingMessage;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import in.wynk.subscription.common.dto.PriceDTO;
import in.wynk.subscription.common.enums.PlanType;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.constant.WalletConstants.WALLET;
import static in.wynk.payment.constant.WalletConstants.PAYTM_WALLET;
import static in.wynk.payment.constant.CardConstants.CARD;
import static in.wynk.payment.core.constant.PaymentConstants.*;

public class PaymentTestUtils {

    public static final String DUMMY_UID = "lViUuniOH80osYFqy0";
    public static final int PLAN_ID = 1000180;
    private static final String DUMMY_MSISDN = "1111111111";

    public static PaymentMethod dummyNetbankingMethod() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("icon_url", "/wp-content/themes/");
        meta.put("promo_msg", "Save and Pay via Cards.");
        meta.put("disable_message", "");
        return PaymentMethod.builder().displayName("Credit / Debit Cards").group("NET_BANKING").hierarchy(10)
                .meta(meta).paymentCode(PAYU).state(State.ACTIVE).build();
    }

    public static PaymentMethod dummyCardMethod() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("icon_url", "/wp-content/themes/");
        meta.put("promo_msg", "Save and Pay via Cards.");
        meta.put("disable_message", "");
        return PaymentMethod.builder().displayName("Credit / Debit Cards").group("CARD").hierarchy(10)
                .meta(meta).paymentCode(PAYU).state(State.ACTIVE).build();
    }

    public static PaymentMethod dummyWalletMethod() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("icon_url", "/wp-content/themes/");
        meta.put("promo_msg", "Save and Pay via Cards.");
        meta.put("disable_message", "");
        return PaymentMethod.builder().displayName("Credit / Debit Cards").group("WALLET").hierarchy(10)
                .meta(meta).paymentCode(PAYTM_WALLET).state(State.ACTIVE).build();
    }

    public static UserPreferredPayment dummyPreferredWallet() {
        return Wallet.builder().id(SavedDetailsKey.builder().paymentCode(PAYTM_WALLET).paymentGroup(WALLET).uid(DUMMY_UID).build()).build();
    }

    public static UserPreferredPayment dummyPreferredCard() {
        return Card.builder().id(SavedDetailsKey.builder().paymentCode(PAYU).paymentGroup(CARD).uid(DUMMY_UID).build()).build();
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
        PlanPeriodDTO planPeriodDTO = PlanPeriodDTO.builder().timeUnit(TimeUnit.DAYS).validity(30).retryInterval(1).build();
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

    public static PaymentRecurringSchedulingMessage getDummyRenewalSubscriptionMessage() {
        return PaymentRecurringSchedulingMessage.builder()
                .planId(1000182)
                .paymentCode("payu")
                .msisdn("+919005334276")
                .uid("zalKRzuDl81b_Av8T0")
                .event(PaymentEvent.SUBSCRIBE)
                .clientAlias("subscriptionApi")
                .nextChargingDate(new Date(System.currentTimeMillis()))
                .build();
    }

}