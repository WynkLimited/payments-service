package in.wynk.payment.dto.addtobill;

import lombok.AllArgsConstructor;
import lombok.Getter;
@AllArgsConstructor
@Getter
public enum ATBOrderStatus {
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    DEFERRED_COMPLETED("DEFERRED_COMPLETED");
    private final String OrderStatus;
}
