package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Transaction;

import java.util.Optional;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Optional<Transaction> get(String id);

}
