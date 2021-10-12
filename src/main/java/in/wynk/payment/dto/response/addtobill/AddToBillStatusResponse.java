package in.wynk.payment.dto.response.addtobill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AddToBillStatusResponse {
    private boolean success;
    private AddToBillStatusResponseBody body;
}
