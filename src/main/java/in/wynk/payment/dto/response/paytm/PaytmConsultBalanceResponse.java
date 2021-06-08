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
    private static class ConsultBalanceResponseBody {

        private PaytmResultInfo resultInfo;
        private List<PaytmPayOption> payOptions;

    }

    public String getResultStatus() {
        return getBody().getResultInfo().getResultStatus();
    }

    public PaytmPayOption getPayOption() {
        return getBody().getPayOptions().get(0);
    }

}