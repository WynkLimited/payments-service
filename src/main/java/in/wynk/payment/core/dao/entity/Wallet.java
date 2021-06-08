package in.wynk.payment.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet extends UserPreferredPayment {

    private long tokenValidity;
    private String accessToken;
    private String refreshToken;
    private String walletUserId;

}