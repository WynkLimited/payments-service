package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SavedWalletDetail extends AbstractSavedDetail {
    private String walletType;
    private String walletId;
}
