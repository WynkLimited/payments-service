package in.wynk.payment.dto.request.common;

import in.wynk.payment.dto.request.common.PromotionDetails;
import in.wynk.payment.dto.request.common.SavedDetails;
import in.wynk.payment.dto.request.common.UiDetails;
import in.wynk.payment.dto.request.wallet.WalletSavedDetails;

/**
 * @author Nishesh Pandey
 */
public class Wallet {
   private String id;
    private String code;
    private String title;
    private String description;
    private WalletSavedDetails savedDetails;
    private PromotionDetails promotionDetails;
    private UiDetails uiDetails;
}
