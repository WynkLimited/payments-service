package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class PayUCardInfo {

    @Getter(onMethod_ = {@JsonGetter(value = "issuingBank")})
    private String issuingBank;

    @Getter(onMethod_ = {@JsonGetter(value = "isDomestic")})
    private String isDomestic;

    @Getter(onMethod_ = {@JsonGetter(value = "cardType")})
    private String cardType;

    @Getter(onMethod_ = {@JsonGetter(value = "cardCategory")})
    private String cardCategory;

    @JsonProperty(value = "is_si_supported", access = JsonProperty.Access.WRITE_ONLY)
    private String siSupport;

    private boolean autoRenewSupported;

    private boolean valid = true;

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

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isAutoRenewSupported() {
        return StringUtils.isNotEmpty(getSiSupport()) ? getSiSupport().equalsIgnoreCase("1"): false;
    }

}
