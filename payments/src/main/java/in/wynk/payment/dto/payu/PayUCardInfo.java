package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.IVerificationResponse;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PayUCardInfo implements IVerificationResponse {

    @Setter
    @Analysed
    @Builder.Default
    private boolean valid = true;

    @Analysed
    private boolean autoRenewSupported;

    @Analysed
    @Getter(onMethod_ = {@JsonGetter(value = "cardType")})
    private String cardType;

    @Analysed
    @Getter(onMethod_ = {@JsonGetter(value = "isDomestic")})
    private String isDomestic;

    @Analysed
    @Getter(onMethod_ = {@JsonGetter(value = "issuingBank")})
    private String issuingBank;

    @Analysed
    @Getter(onMethod_ = {@JsonGetter(value = "cardCategory")})
    private String cardCategory;

    @JsonProperty(value = "is_si_supported", access = JsonProperty.Access.WRITE_ONLY)
    private String siSupport;

    @JsonProperty(value = "issuing_bank")
    public void setIssuingBank(String issuingBank) {
        this.issuingBank = issuingBank;
    }

    @JsonProperty(value = "is_domestic")
    public void setIsDomestic(String isDomestic) {
        this.isDomestic = StringUtils.isNotEmpty(isDomestic) && isDomestic.equalsIgnoreCase("1") ? "Y" : "N";
    }

    @JsonProperty(value = "card_type")
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    @JsonProperty(value = "category")
    public void setCardCategory(String cardCategory) {
        this.cardCategory = cardCategory;
    }

    public boolean isAutoRenewSupported() {
        return StringUtils.isNotEmpty(getSiSupport()) ? getSiSupport().equalsIgnoreCase("1") : false;
    }

}