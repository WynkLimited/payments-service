package in.wynk.payment.core.entity;


import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static in.wynk.commons.enums.PaymentGroup.CARD;

@Getter
public class Card implements Payment {

    private PaymentGroup group = CARD;
    private List<CardDetails> cardDetails;
    private PaymentCode paymentCode;


    private Card() {
    }

    public static final class Builder {
        private PaymentGroup group = CARD;
        private PaymentCode paymentCode;
        private List<CardDetails> cardDetails = new ArrayList<>();

        public Builder() {
        }

        public Builder paymentCode(PaymentCode paymentCode) {
            this.paymentCode = paymentCode;
            return this;
        }

        public Builder cardDetails(CardDetails cardDetails) {
            this.cardDetails.add(cardDetails);
            return this;
        }

        public Card build() {
            Card card = new Card();
            card.paymentCode = this.paymentCode;
            card.group = this.group;
            card.cardDetails = this.cardDetails;
            return card;
        }
    }

    @lombok.Builder
    @Data
    public static class CardDetails {
        private String cardToken;
    }
}