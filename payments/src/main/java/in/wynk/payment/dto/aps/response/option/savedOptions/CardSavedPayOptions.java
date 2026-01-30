package in.wynk.payment.dto.aps.response.option.savedOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.CardConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

import static in.wynk.payment.dto.aps.common.ApsConstant.APS;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardSavedPayOptions extends AbstractSavedPayOptions implements Serializable {
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

    @Override
    public String getId() {
        return APS.concat("_").concat(CardConstants.CARD);
    }
}
