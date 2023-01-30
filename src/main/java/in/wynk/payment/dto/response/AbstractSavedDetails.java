package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.PaymentDetails;
import in.wynk.payment.dto.response.billing.SavedBillingPayment;
import in.wynk.payment.dto.response.card.CardDetails;
import in.wynk.payment.dto.response.upi.UpiSavedDetails;
import in.wynk.payment.dto.response.wallet.SavedWalletPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import net.bytebuddy.implementation.bind.annotation.Super;
import org.springframework.data.annotation.PersistenceConstructor;


/**
 * @author Nishesh Pandey
 */
@Getter
@AllArgsConstructor
@SuperBuilder
@AnalysedEntity
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentMode", visible = true, defaultImpl = PaymentDetails.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiSavedDetails.class, name =  PaymentConstants.UPI),
        @JsonSubTypes.Type(value = CardDetails.class, name = PaymentConstants.CARD),
        @JsonSubTypes.Type(value = SavedWalletPayment.class, name = PaymentConstants.WALLET),
        @JsonSubTypes.Type(value = SavedBillingPayment.class, name =  PaymentConstants.BILLING)
})
public abstract class AbstractSavedDetails {
    private String id;

    @PersistenceConstructor
    public AbstractSavedDetails() {
    }
}
