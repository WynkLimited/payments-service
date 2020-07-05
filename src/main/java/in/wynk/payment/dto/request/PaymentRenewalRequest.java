package in.wynk.payment.dto.request;

import in.wynk.payment.core.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class PaymentRenewalRequest {

    private Transaction previousTransaction;
}
