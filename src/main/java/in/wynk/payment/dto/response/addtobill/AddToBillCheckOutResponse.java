package in.wynk.payment.dto.response.addtobill;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AddToBillCheckOutResponse {
    private boolean success;
    private CheckOutResponse body;
    @Builder
    @Getter
    public static class CheckOutResponse {
        private String orderId;
    }
}
