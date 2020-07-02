package in.wynk.payment.service;

import in.wynk.payment.core.dto.Transaction;

import java.util.UUID;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Transaction get(String id);

}
