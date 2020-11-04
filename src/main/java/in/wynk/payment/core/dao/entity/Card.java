package in.wynk.payment.core.dao.entity;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.enums.PaymentGroup;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static in.wynk.payment.core.enums.PaymentGroup.CARD;

@Getter
@NoArgsConstructor
public class Card implements Payment {

    private PaymentGroup group = CARD;
    private List<CardDetails> cardDetails;
    private PaymentCode paymentCode;


    public static final class Builder {
        private final PaymentGroup group = CARD;
        private PaymentCode paymentCode;
        private final List<CardDetails> cardDetails = new ArrayList<>();

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