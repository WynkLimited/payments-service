package in.wynk.payment.dto.common.response;

import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */

@Getter
@ToString
@SuperBuilder
public class PaymentStatusWrapper {
    private int planId;
    private Transaction transaction;
    private ErrorCode errorCode;
    private String paymentId;
}
