package in.wynk.payment.dto.phonepe;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Deprecated
public class PhonePeAutoDebitRequest {

    private long txnAmount;
    private String merchantId;
    private String userAuthToken;

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