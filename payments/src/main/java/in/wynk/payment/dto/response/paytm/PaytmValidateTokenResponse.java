package in.wynk.payment.dto.response.paytm;

import lombok.Getter;

@Getter
public class PaytmValidateTokenResponse {

    private String id;
    private String email;
    private String mobile;
    private long expires;

}
