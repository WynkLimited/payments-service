package in.wynk.payment.dto;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class GenerateItemEvent {
    private String transactionId;
    private String itemId;
    private String uid;
    private String createdDate;
    private String updatedDate;
    private TransactionStatus transactionStatus;
    private PaymentEvent event;
}
