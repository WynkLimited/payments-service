package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.Transaction;

public class TransactionContext {
    private static final ThreadLocal<TransactionDetails> transactionThreadLocal = new ThreadLocal<>();

    public static void set(TransactionDetails transactionDetails) {
        transactionThreadLocal.set(transactionDetails);
    }

    public static Transaction get() {
        return transactionThreadLocal.get().getTransaction();
    }

    public static PayerDetails getPayerDetails() {
        return transactionThreadLocal.get().getPayerDetails();
    }

}