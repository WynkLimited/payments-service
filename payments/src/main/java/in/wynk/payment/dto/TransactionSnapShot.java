package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionSnapShot {
    private final TransactionDetails transactionDetails;
}
