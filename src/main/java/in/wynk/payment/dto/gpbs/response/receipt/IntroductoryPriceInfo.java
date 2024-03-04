package in.wynk.payment.dto.gpbs.response.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntroductoryPriceInfo {
    private String introductoryPriceCurrencyCode;
    private String introductoryPriceAmountMicros;
    private String introductoryPricePeriod;
    private String introductoryPriceCycles;
}
