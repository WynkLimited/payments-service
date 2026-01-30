package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;

import java.util.Set;

public interface ITransactionManagerService {

    Transaction get(String id);

   Transaction init(AbstractTransactionInitRequest transactionInitRequest);

   Transaction init(AbstractTransactionInitRequest transactionInitRequest, IPurchaseDetails purchaseDetails);

    Transaction init(AbstractTransactionInitRequest transactionInitRequest, AbstractPaymentChargingRequest request);

    void revision(AbstractTransactionRevisionRequest request);

    Set<Transaction> getAll(Set<String> idList);

    void migrateOldTransactions(String userId, String uid, String oldUid, String service);

    Transaction upsert (Transaction transaction);

    Transaction getByOriginalTransactionId(String id);

}
