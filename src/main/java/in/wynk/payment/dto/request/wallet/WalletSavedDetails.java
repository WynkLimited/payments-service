package in.wynk.payment.dto.request.wallet;

import in.wynk.payment.dto.request.common.SavedDetails;

/**
 * @author Nishesh Pandey
 */
public class WalletSavedDetails extends SavedDetails {
    private String linkedMobile;
    private Double balance;
    private boolean addMoneyRequired;
    private boolean canCheckout;
}
