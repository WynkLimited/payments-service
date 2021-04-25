package in.wynk.payment.dto.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaytmConsultBalanceRequest {

    private PaytmRequestHead head;
    private ConsultBalanceRequestBody body;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsultBalanceRequestBody {

        private String mid;
        private double txnAmount;
        private String userToken;
        private Object subwalletAmount;
        private boolean showExpiredMerchantGVBalance;

    }

}
