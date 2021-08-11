package in.wynk.payment.core.event;

import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class TransactionSnapshotEvent {
    private final Transaction transaction;
    private final Optional<IPaymentDetails> paymentDetails;
}