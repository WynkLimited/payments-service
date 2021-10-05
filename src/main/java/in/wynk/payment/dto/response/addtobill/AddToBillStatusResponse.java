package in.wynk.payment.dto.response.addtobill;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AddToBillStatusResponse {
    private boolean success;
    private AddToBillStatusResponseBody body;
}
