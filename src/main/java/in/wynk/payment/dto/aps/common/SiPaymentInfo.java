package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
public class SiPaymentInfo extends AbstractPaymentInfo {
    private String mandateTransactionId;
    private String paymentGateway;
}
