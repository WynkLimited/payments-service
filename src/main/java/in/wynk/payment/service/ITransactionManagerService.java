package in.wynk.payment.service;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;

import in.wynk.payment.dto.request.TransactionInitRequest;
import java.util.function.Consumer;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Transaction get(String id);

    //TODO: Remove
    Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, TransactionEvent event);

    Transaction initiateTransaction(TransactionInitRequest transactionInitRequest);

    //TODO: Remove
    void updateAndPublishSync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn);

    //TODO: Remove
    void updateAndPublishAsync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn);

    void updateAndSyncPublish(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus);

    void updateAndAsyncPublish(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus);

}
