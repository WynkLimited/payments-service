package in.wynk.payment.dto.aps.request.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class ApsMandateStatusCallbackRequestPayload extends ApsCallBackRequestPayload {
    private Long mandateStartDate;
    private Long mandateEndDate;
}
