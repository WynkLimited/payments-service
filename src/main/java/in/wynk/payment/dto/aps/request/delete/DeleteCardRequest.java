package in.wynk.payment.dto.aps.request.delete;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class DeleteCardRequest {
    private String referenceNumber;
}
