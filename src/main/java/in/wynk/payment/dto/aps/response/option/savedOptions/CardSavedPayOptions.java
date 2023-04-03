package in.wynk.payment.dto.aps.response.option.savedOptions;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class CardSavedPayOptions extends AbstractSavedPayOptions {
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
    private String tokenizationStatusMsg;
    private boolean showTokenizationConsent;
    private String iconUrl;
    private boolean expired;
}
