package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public abstract class PaytmResponseBody {
    private PaytmResultInfo resultInfo;
}