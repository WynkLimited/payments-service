package in.wynk.payment.dto.aps.response.option;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@ToString
public class WalletSavedOptions {
    private String walletType;
    private String walletId;
    private String walletBalance;
    private boolean recommended;
    private boolean isLinked;
}
