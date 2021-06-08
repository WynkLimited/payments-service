package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;

@Getter
@NoArgsConstructor
public class PaytmRefreshTokenResponse {

    private List<Token> tokens;

    @Getter
    @NoArgsConstructor
    private static class Token {

        private String scope;
        private String accessToken;
        private String tokenType;
        private long expiresIn;

    }

    private Token getToken() {
        return tokens.stream().max(Comparator.comparingLong(Token::getExpiresIn)).get();
    }

    public String getAccessToken() {
        return getToken().getAccessToken();
    }

    public long getExpiresIn() {
        return getToken().getExpiresIn();
    }

}