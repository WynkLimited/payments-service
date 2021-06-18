package in.wynk.payment.dto.paytm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaytmRefreshTokenRequest {

    private String grantType;
    private String deviceId;
    private String refreshToken;

}
