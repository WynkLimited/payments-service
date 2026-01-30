package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmRefundResponseBody extends PaytmTransactionalResponseBody {

    private String refId;
    private String refundId;
    private String refundAmount;
    private String txnTimestamp;

}