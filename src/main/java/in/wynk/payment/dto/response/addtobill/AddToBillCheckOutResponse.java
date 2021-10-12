package in.wynk.payment.dto.response.addtobill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AddToBillCheckOutResponse {
    private boolean success;
    private CheckOutResponse body;

    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    @Getter
    public static class CheckOutResponse {
        private String orderId;
    }
}
