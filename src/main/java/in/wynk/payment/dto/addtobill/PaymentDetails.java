package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentDetails {
    private double paymentAmount;
}
