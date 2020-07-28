package in.wynk.payment.dto.paytm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaytmWalletOtpRequest {

    private String email;

    private String phone;

    private String clientId;

    private String scope;

    private String responseType;

}
