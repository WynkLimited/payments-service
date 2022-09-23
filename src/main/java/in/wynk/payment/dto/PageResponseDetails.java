package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class PageResponseDetails {

    @Analysed
    @JsonProperty("page_url")
    private String pageUrl;
}
