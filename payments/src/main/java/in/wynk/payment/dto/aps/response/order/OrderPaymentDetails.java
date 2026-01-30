package in.wynk.payment.dto.aps.response.order;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class OrderPaymentDetails {
    private String pgId;
    private String orderId;
    private String eventName;
    private double paymentAmount;
    private String paymentMode;
    private String paymentModeLabel;
    private String bankName;
    private String lob;
    private String paymentStatus;
    private String paymentGateway;
    private long paymentDate;
    private String bankCode;
    private String mid;
    private String paymentCode;
    private long paymentStartDate;
    private long paymentEndDate;
    private String paymentRoutedThrough;
    private String billingReceiptId;
    private String cardNetwork;
    private String errorCode;
    private String errorDescription;
}
