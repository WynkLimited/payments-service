package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.PaymentDetails;
import in.wynk.payment.dto.response.billing.SavedBillingPayment;
import in.wynk.payment.dto.response.card.CardDetails;
import in.wynk.payment.dto.response.wallet.SavedWalletPayment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.PersistenceConstructor;
import static in.wynk.payment.core.constant.CardConstants.CARD;
import static in.wynk.payment.core.constant.WalletConstants.WALLET;


/**
 * @author Nishesh Pandey
 */
@Getter
@AllArgsConstructor
@SuperBuilder
@AnalysedEntity
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentMode", visible = true, defaultImpl = PaymentDetails.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CardDetails.class, name = CARD),
        @JsonSubTypes.Type(value = SavedWalletPayment.class, name = WALLET),
        @JsonSubTypes.Type(value = SavedBillingPayment.class, name =  PaymentConstants.BILLING)
})
public abstract class AbstractSavedDetails {
    private String id;

    @PersistenceConstructor
    public AbstractSavedDetails() {
    }
}
