package in.wynk.payment.dto.paytm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaytmWalletOtpRequest {

    private String phone;
    private List<String> scopes;

}