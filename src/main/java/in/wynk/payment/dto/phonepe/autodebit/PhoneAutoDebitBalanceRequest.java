package in.wynk.payment.dto.phonepe.autodebit;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PhoneAutoDebitBalanceRequest {
    private long txnAmount;
    private String merchantId;
    private String userAuthToken;
    public long getTxnAmount(){
        return txnAmount*100;
    }
}
