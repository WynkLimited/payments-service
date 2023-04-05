package in.wynk.payment.dto.common;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class WalletSavedInfo extends AbstractSavedInstrumentInfo {
    private boolean linked;
    private double balance;
    private String walletId;
    private double minBalance;
    private boolean addMoneyAllowed;
}
