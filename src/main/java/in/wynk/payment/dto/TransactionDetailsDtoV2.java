package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TransactionDetailsDtoV2 extends TransactionDetailsDto {
    private final PaymentErrorDetails errorDetails;
}
