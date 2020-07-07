package in.wynk.payment.core.entity;


import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;

import static in.wynk.commons.enums.PaymentGroup.CARD;

@Getter
public class Card implements Payment {

    private PaymentGroup group = CARD;
    private PaymentCode paymentCode;


    private Card() {
    }

    public static final class Builder {
        private PaymentGroup group = CARD;
        private PaymentCode paymentCode;

        public Builder() {
        }

        public Builder paymentCode(PaymentCode paymentCode) {
            this.paymentCode = paymentCode;
            return this;
        }

        public Card build() {
            Card card = new Card();
            card.paymentCode = this.paymentCode;
            card.group = this.group;
            return card;
        }
    }
}