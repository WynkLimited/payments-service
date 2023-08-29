package in.wynk.payment.core.dto;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Calendar;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {
    private String id;
    private Integer planId;
    private double amount;
    private double mandateAmount;
    private double discount;
    private Calendar initTime;
    private String uid;
    private String msisdn;
    private String clientAlias;
    private String itemId;
    private String paymentChannel;
    private String type;
    private String status;
    private String coupon;
    private Calendar exitTime;
    private Calendar consent;

    public static TransactionDTO from(Transaction transaction){
        return TransactionDTO.builder()
                .id(transaction.getIdStr())
                .planId(transaction.getPlanId())
                .amount(transaction.getAmount())
                .mandateAmount(transaction.getMandateAmount())
                .discount(transaction.getDiscount())
                .initTime(transaction.getInitTime())
                .uid(transaction.getUid())
                .msisdn(transaction.getMsisdn())
                .clientAlias(transaction.getClientAlias())
                .itemId(transaction.getItemId())
                .paymentChannel(transaction.getPaymentChannel().getCode())
                .type(transaction.getType().getValue())
                .status(transaction.getStatus().getValue())
                .coupon(transaction.getCoupon())
                .exitTime(transaction.getExitTime())
                .consent(transaction.getConsent()).build();
    }
}