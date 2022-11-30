package in.wynk.payment.dto;

import in.wynk.common.enums.TransactionStatus;
import lombok.experimental.SuperBuilder;
import lombok.Getter;

import java.util.Calendar;

@SuperBuilder
@Getter
public class TransactionDetailsDto {
    private final String tid;
    private final int planId;
    private final double amountPaid;
    private final double discount;
    private final String type;
    private final long validity;
    private final TransactionStatus status;
    private final Calendar creationDate;
    private final AbstractPack packDetails;
    private final PurchaseDetailsDto purchaseDetails;

}
