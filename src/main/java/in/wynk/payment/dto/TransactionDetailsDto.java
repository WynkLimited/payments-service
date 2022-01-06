package in.wynk.payment.dto;

import in.wynk.common.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Calendar;

@Builder
@Getter
public class TransactionDetailsDto {
    private final double amountPaid;
    private final double discount;
    private final String type;
    private final String transactionId;
    private final TransactionStatus status;
    private final Calendar creationDate;
    private final AbstractPack packDetails;
    private final PurchaseDetailsDto purchaseDetails;

}
