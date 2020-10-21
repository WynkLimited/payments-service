package in.wynk.payment.core.dao.entity;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.enums.PaymentGroup;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static in.wynk.payment.core.enums.PaymentGroup.WALLET;

@Getter
@NoArgsConstructor
public class Wallet implements Payment {

    private PaymentGroup group = WALLET;
    private PaymentCode paymentCode;
    @Setter
    private String accessToken;
    @Setter
    private String walletUserId;
    @Setter
    private long tokenValidity;


    public static final class Builder {
        private final PaymentGroup group = WALLET;
        private PaymentCode paymentCode;
        private String accessToken;
        private String walletUserId;
        private long tokenValidity;

        public Builder() {
        }


        public Builder tokenValidity(long tokenValidity) {
            this.tokenValidity = tokenValidity;
            return this;
        }

        public Builder paymentCode(PaymentCode paymentCode) {
            this.paymentCode = paymentCode;
            return this;
        }

        public Builder walletUserId(String walletUserId) {
            this.walletUserId = walletUserId;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Wallet build() {
            Wallet wallet = new Wallet();
            wallet.paymentCode = this.paymentCode;
            wallet.accessToken = this.accessToken;
            wallet.walletUserId = this.walletUserId;
            wallet.tokenValidity = this.tokenValidity;
            wallet.group = this.group;
            return wallet;
        }
    }
}