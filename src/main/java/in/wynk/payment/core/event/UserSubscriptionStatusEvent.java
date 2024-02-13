package in.wynk.payment.core.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionStatusEvent {
    private String si;
    private String status;
}
