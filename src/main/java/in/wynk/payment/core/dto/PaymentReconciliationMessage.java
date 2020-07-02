package in.wynk.payment.core.dto;

import in.wynk.commons.dto.PackPeriodDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.revenue.commons.TransactionEvent;
import lombok.*;

import java.util.Date;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReconciliationMessage {
    private int planId;
    private String uid;
    private String transactionId;
    private Date initTimestamp;
    private PaymentCode paymentCode;
    private TransactionEvent transactionEvent;
    private PackPeriodDTO packPeriod;

}
