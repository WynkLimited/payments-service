package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;

@Getter
@NoArgsConstructor
public class PaytmWalletValidateLinkResponse extends PaytmCustomResponse {

    private List<Token> tokens;
    private String encryptedUserId;

    @Getter
    @NoArgsConstructor
    private static class Token {

         private String accessToken;
         private String refreshToken;
         private long expiry;
         private String scope;

    }

    private Token getToken() {
        return tokens.stream().max(Comparator.comparingLong(Token::getExpiry)).get();
    }

    public String getAccessToken() {
        return getToken().getAccessToken();
    }

    public String getRefreshToken() {
        return getToken().getRefreshToken();
    }

    public long getExpiry() {
        return getToken().getExpiry();
    }

}
