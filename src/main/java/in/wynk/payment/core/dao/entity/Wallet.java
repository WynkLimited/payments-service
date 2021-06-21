package in.wynk.payment.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet extends UserPreferredPayment {

    @Field("token_validity")
    private long tokenValidity;

    @Field("access_token")
    private String accessToken;

    @Field("refresh_token")
    private String refreshToken;

    @Field("wallet_user_id")
    private String walletUserId;

}