package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.AppDetails;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class GooglePlayAppDetails extends AppDetails {
    @Analysed
    private String packageName;
}
