package in.wynk.payment.dto.paytm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaytmStatusRequest {

    private PaytmRequestHead head;
    private PaytmStatusRequestBody body;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaytmStatusRequestBody {

        private String mid;
        private String orderId;
        private String txnType;

    }

}
