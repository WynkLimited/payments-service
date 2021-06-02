package in.wynk.payment.dto.phonepe;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class PhonePeAutoDebitRequest {
    private String merchantId;
    private String userAuthToken;
    private long txnAmount;

    public long getTxnAmount(){
        return txnAmount*100;
    }

    public String getMerchantId() {
        return this.merchantId;
    }

    public String getUserAuthToken() {
        return this.userAuthToken;
    }
}
