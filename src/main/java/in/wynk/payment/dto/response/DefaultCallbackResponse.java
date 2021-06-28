package in.wynk.payment.dto.response;

import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class DefaultCallbackResponse extends AbstractCallbackResponse {
    private TransactionStatus transactionStatus;
}
