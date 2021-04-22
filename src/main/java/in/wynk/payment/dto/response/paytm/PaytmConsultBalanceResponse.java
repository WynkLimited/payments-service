package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PaytmConsultBalanceResponse {

    private PaytmResponseHead head;
    private ConsultBalanceResponseBody body;

    @Getter
    @NoArgsConstructor
    public static class ConsultBalanceResponseBody {

        private PaytmResultInfo resultInfo;
        private List<PaytmPayOption> payOptions;

    }

}