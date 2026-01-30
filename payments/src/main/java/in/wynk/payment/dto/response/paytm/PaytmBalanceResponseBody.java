package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PaytmBalanceResponseBody extends PaytmResponseBody {
    private List<PaytmPayOption> payOptions;
}