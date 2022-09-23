package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class SessionDetails {

    @Analysed
    private String sessionId;
}
