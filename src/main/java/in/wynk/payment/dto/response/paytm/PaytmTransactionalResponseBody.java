package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public abstract class PaytmTransactionalResponseBody extends PaytmResponseBody {

    private String mid;
    private String txnId;
    private String orderId;

}