package in.wynk.payment.dto.response.paytm;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaytmWalletBalanceResponse {

    private Double balance;
    private Double deficitBalance;
    private boolean fundsSufficient;
    private boolean isLinked;

    public static PaytmWalletBalanceResponse defaultUnlinkResponse(){
        return PaytmWalletBalanceResponse.builder().isLinked(false).build();
    }

}
