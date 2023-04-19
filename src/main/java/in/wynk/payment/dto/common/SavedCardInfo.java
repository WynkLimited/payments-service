package in.wynk.payment.dto.common;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class SavedCardInfo extends AbstractSavedInstrumentInfo {
    private String createdOn;
    private String updatedOn;
    private String lastUsedOn;
    private String cardRefNo;
    private String cardNumber;
    private String cardBankName;
    private String expiryMonth;
    private String expiryYear;
    private String cardType;
    private String cardCategory;
    private String maskedCardNumber;
    private String cardStatus;
    private String cardBin;
    private String bankCode;
    private String cvvLength;
    private boolean blocked;
    private boolean autoPayEnable;
    private boolean tokenized;
    private boolean expired;
}
