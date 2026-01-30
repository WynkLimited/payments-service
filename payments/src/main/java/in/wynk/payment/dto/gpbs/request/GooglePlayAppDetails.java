package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.AppDetails;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class GooglePlayAppDetails extends AppDetails {
    @Analysed
    @NotEmpty
    private String packageName;
}
