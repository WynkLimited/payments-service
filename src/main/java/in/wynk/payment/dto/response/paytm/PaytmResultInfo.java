package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmResultInfo {

    private String resultCode;
    private String resultStatus;
    private String resultMsg;

}
