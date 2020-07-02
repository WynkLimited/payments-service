package in.wynk.payment.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletBalanceResponse {

    private Double balance;
    private Double deficitBalance;
    private boolean fundsSufficient;
    private boolean isLinked;

    public static WalletBalanceResponse defaultUnlinkResponse(){
        return WalletBalanceResponse.builder().isLinked(false).build();
    }

}
