package in.wynk.payment.dto;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class GenerateItemEvent {
    private String transactionId;
    private String itemId;
    private String uid;
    private Double price;
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar createdDate;
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar updatedDate;
    private TransactionStatus transactionStatus;
    private PaymentEvent event;
}
