package in.wynk.payment.core.event;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
@Builder
@Data
public class PaymentsBranchEvent<T> {
    private T data;
    private String eventName;
}
