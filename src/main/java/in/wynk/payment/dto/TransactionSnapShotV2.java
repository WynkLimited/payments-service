package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
//@Builder
@SuperBuilder
public class TransactionSnapShotV2 extends TransactionSnapShot{
    private final PaymentErrorDetails errorDetails;
}
