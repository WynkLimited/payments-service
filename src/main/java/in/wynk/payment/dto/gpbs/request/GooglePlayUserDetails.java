package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.UserDetails;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;

/**
 * @author Nishesh Pandey
 */
@Setter
@Getter
public class GooglePlayUserDetails extends UserDetails {
    @Analysed
    @NotEmpty
    private String uid;
}
