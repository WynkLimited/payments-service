package in.wynk.payment.dto.common;

import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public abstract class AbstractPreDebitNotificationResponse {
    private String tid;
    private TransactionStatus transactionStatus;
    private String requestId;
}
