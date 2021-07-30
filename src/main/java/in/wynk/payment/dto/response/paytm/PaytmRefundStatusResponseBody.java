package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PaytmRefundStatusResponseBody extends PaytmRefundResponseBody {

    private String txnAmount;
    private String totalRefundAmount;
    private String acceptRefundStatus;
    private String userCreditInitiateStatus;
    private String merchantRefundRequestTimestamp;
    private String acceptRefundTimestamp;
    private String userCreditInitiateTimestamp;
    private List<Object> refundDetailInfoList;
    private String refundReason;
    private Object agentInfo;

}