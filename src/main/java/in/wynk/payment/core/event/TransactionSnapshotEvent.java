package in.wynk.payment.core.event;

import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TransactionSnapshotEvent {
    private final Transaction transaction;
    private final IPaymentDetails paymentDetails;
}