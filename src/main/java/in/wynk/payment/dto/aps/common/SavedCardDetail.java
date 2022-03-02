package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SavedCardDetail extends AbstractSavedDetail {
    private String cardRefNo;
    private String cardNumber;
    private String cardBankName;
    private String expiryMonth;
    private String expiryYear;
    private String cardType;
    private String cardCategory;
    private String nameOnCard;
    private String maskedCardNumber;
    private String cardStatus;
    private String cardStatusMsg;
    private String cardBin;
    private boolean autoPayEnable;
}
