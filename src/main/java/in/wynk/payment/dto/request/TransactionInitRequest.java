package in.wynk.payment.dto.request;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInitRequest {

    private int planId;

    private double amount;
    private double discount;

    private String uid;
    private String msisdn;
    private String itemId;
    private String couponId;
    private String clientAlias;
    @Builder.Default
    private String status = TransactionStatus.INPROGRESS.getValue();

    private PaymentEvent event;
    private PaymentGateway paymentGateway;

}