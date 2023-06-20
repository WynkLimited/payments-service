package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;

import java.util.Optional;

public class TransactionContext {
    private static final ThreadLocal<TransactionDetails> transactionThreadLocal = new ThreadLocal<>();

    public static void set(TransactionDetails transactionDetails) {
        transactionThreadLocal.set(transactionDetails);
    }

    public static Transaction get() {
        return transactionThreadLocal.get().getTransaction();
    }

    public static void clear() {
        transactionThreadLocal.remove();
    }

    public static Optional<IPurchaseDetails> getPurchaseDetails() {
        return Optional.ofNullable(transactionThreadLocal.get().getPurchaseDetails());
    }

}