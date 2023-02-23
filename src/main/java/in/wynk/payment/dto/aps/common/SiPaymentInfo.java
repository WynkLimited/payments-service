package in.wynk.payment.dto.aps.common;

import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.Builder;
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
    private String invoiceNumber;
    @Builder.Default
    private String lob = BaseConstants.WYNK;
    @Builder.Default
    private String paymentGateway = PaymentConstants.PAYU;//APS has contract with PayU only for AutoPay
}
