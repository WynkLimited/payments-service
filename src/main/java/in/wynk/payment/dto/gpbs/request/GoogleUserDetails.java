package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.UserDetails;

/**
 * @author Nishesh Pandey
 */
public class GoogleUserDetails extends UserDetails {
    @Analysed
    private String uid;
}
