package in.wynk.payment;

import in.wynk.payment.core.dao.entity.Transaction;

/**
 * @author Abhishek
 * @created 13/08/20
 */
public class TransactionContext {
    private static final ThreadLocal<Transaction> transactionThreadLocal = new ThreadLocal<>();

    public static <T> void set(Transaction transaction) {
        transactionThreadLocal.set(transaction);
    }

    public static <T> Transaction get() {
        return transactionThreadLocal.get();
    }
}