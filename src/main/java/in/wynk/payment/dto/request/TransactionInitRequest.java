package in.wynk.payment.dto.request;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Abhishek
 * @created 17/08/20
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInitRequest {
    private String uid;
    private String msisdn;
    private String clientAlias;
    private int planId;
    private String itemId;
    private double amount;
    private PaymentCode paymentCode;
    private PaymentEvent event;
    private String couponId;
    private double discount;
    @Builder.Default
    private String status = TransactionStatus.INPROGRESS.getValue();
}
