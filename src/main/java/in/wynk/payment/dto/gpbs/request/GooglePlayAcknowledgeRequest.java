package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@Builder
public class GooglePlayAcknowledgeRequest {
    @Analysed
    private String developerPayload;
}
