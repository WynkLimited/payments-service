package in.wynk.payment.core.dao.entity;


import in.wynk.commons.enums.PaymentGroup;
import lombok.Getter;

import static in.wynk.commons.enums.PaymentGroup.WALLET;

@Getter
public class Wallets implements PaymentOption {

    private PaymentGroup group = WALLET;


    public static WalletsBuilder builder() {
        return new WalletsBuilder();
    }

    public static final class WalletsBuilder {
        private PaymentGroup type = WALLET;

        public WalletsBuilder() {
        }


        public Wallets build() {
            Wallets cards = new Wallets();
            cards.group = this.type;
            return cards;
        }
    }
}