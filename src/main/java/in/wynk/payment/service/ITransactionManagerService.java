package in.wynk.payment.service;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.enums.PaymentCode;

import java.util.function.Consumer;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Transaction get(String id);

    Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, TransactionEvent event);

    void updateAndPublishSync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn);

    void updateAndPublishAsync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn);


}
