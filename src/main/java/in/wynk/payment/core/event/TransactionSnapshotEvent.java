package in.wynk.payment.core.event;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSnapshotEvent {
    private Transaction transaction;
    private IPurchaseDetails purchaseDetails;
}