package in.wynk.payment.dto.request.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

import static in.wynk.payment.constant.CardConstants.FRESH_CARD_TYPE;
import static in.wynk.payment.constant.CardConstants.SAVED_CARD_TYPE;

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
public abstract class AbstractCardDetails implements Serializable {

    @Analysed
    private CardInfo cardInfo;

    @Analysed
    @JsonProperty("isSaveCard")
    private boolean saveCard;

    @Analysed
    @JsonProperty("inAppOtpSupport")
    private Boolean inAppOtpSupport;

    @Analysed
    @JsonProperty("otpLessSupport")
    private Boolean otpLessSupport;

    @Analysed(name = "cardDetailsType")
    public abstract String getType();

}
