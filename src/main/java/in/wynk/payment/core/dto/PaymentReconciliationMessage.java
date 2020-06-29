package in.wynk.payment.core.dto;

import lombok.*;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReconciliationMessage {

    private String uid;
    private String transactionId;
    private int planId;

}
