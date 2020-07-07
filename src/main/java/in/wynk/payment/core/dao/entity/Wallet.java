package in.wynk.payment.core.dao.entity;


import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;

import static in.wynk.commons.enums.PaymentGroup.WALLET;

@Getter
public class Wallet implements Payment {

    private PaymentGroup group = WALLET;
    private PaymentCode paymentCode;


    public static final class Builder {
        private PaymentGroup group = WALLET;
        private PaymentCode paymentCode;

        public Builder() {
        }


        public Builder paymentCode(PaymentCode paymentCode) {
            this.paymentCode = paymentCode;
            return this;
        }

        public Wallet build() {
            Wallet wallet = new Wallet();
            wallet.paymentCode = this.paymentCode;
            wallet.group = this.group;
            return wallet;
        }
    }
}