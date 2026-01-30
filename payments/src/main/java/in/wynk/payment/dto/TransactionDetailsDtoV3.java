package in.wynk.payment.dto;

import lombok.experimental.SuperBuilder;
import lombok.Getter;

@SuperBuilder
@Getter
public class TransactionDetailsDtoV3 extends TransactionDetailsDto {
    private final PaymentErrorDetails errorDetails;
}
