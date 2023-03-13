package in.wynk.payment.dto.aps.common;

import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.apb.ApbConstants;
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
    /**
     * Static value SI_WYNK for renewal transactions
     */
    @Builder.Default
    private String lob = ApbConstants.LOB_SI_WYNK;

    private String paymentGateway;
}
