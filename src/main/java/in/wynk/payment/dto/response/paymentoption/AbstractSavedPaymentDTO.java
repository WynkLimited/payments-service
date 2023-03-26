package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@SuperBuilder
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "group", visible = true, defaultImpl = SavedPaymentDTO.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiSavedDetails.class, name = "UPI"),
        @JsonSubTypes.Type(value = WalletSavedDetails.class, name = "WALLET"),
        @JsonSubTypes.Type(value = CardSavedDetails.class, name = "CARD")
})
public class AbstractSavedPaymentDTO extends SavedDetails {
}
