package in.wynk.payment.core.dao.entity;


import in.wynk.commons.enums.PaymentGroup;
import lombok.Getter;

import static in.wynk.commons.enums.PaymentGroup.CARD;

@Getter
public class Cards implements PaymentOption {

    private PaymentGroup group = CARD;


    private Cards() {
    }

    public static CardBuilder builder() {
        return new CardBuilder();
    }

    public static final class CardBuilder {
        private PaymentGroup type = CARD;

        public CardBuilder() {
        }


        public Cards build() {
            Cards cards = new Cards();
            cards.group = this.type;
            return cards;
        }
    }
}