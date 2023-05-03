package in.wynk.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@NoArgsConstructor
public abstract class AbstractCancelMandateRequest {
    private String tid;
    private String msisdn;
}
