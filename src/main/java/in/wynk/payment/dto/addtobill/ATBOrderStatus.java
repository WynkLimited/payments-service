package in.wynk.payment.dto.addtobill;

import lombok.AllArgsConstructor;
import lombok.Getter;
@AllArgsConstructor
@Getter
public enum ATBOrderStatus {
    COMPLETED("COMPLETED"),
    FAILED("FAILED");
    private final String OrderStatus;
}
