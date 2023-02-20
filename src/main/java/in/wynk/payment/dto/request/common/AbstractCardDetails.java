package in.wynk.payment.dto.request.common;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import static in.wynk.payment.dto.apb.ApbConstants.FRESH_CARD_TYPE;
import static in.wynk.payment.dto.apb.ApbConstants.SAVED_CARD_TYPE;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SavedCardDetails.class, name = SAVED_CARD_TYPE),
        @JsonSubTypes.Type(value = FreshCardDetails.class, name = FRESH_CARD_TYPE)
})
public abstract class AbstractCardDetails {

    @Analysed
    private CardInfo cardInfo;

    @Analysed
    private boolean isSaveCard;

    @Analysed(name = "cardDetailsType")
    public abstract String getType();

}
