package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.aps.response.option.DefaultSavedPayOptions;
import lombok.*;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "group", visible = true, defaultImpl = DefaultSavedPaymentDTO.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiSavedDetails.class, name = "UPI"),
        @JsonSubTypes.Type(value = WalletSavedDetails.class, name = "WALLET"),
        @JsonSubTypes.Type(value = CardSavedDetails.class, name = "CARD")
})
public abstract class AbstractSavedPaymentDTO extends AbstractSavedDetails {
    private String group;
}
