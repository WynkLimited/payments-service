package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderPaymentDetails {
    private boolean addToBill;
    private double orderPaymentAmount;
    private String paymentTransactionId;
    private OptedPaymentMode optedPaymentMode;
}
