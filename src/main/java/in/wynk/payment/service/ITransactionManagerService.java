package in.wynk.payment.service;

import in.wynk.payment.core.entity.Transaction;

import java.util.UUID;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Transaction get(UUID id);

}
