package in.wynk.payment.dto;

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
    private String pageUrl;
}
