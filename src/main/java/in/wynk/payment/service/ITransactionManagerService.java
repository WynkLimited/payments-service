package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;

import java.util.List;

public interface ITransactionManagerService {

    Transaction get(String id);

   Transaction init(AbstractTransactionInitRequest transactionInitRequest);

   Transaction init(AbstractTransactionInitRequest transactionInitRequest, IPurchaseDetails purchaseDetails);

    void revision(AbstractTransactionRevisionRequest request);

    List<Transaction> getAll(List<String> idList);

    void migrateOldTransactions(String userId, String uid, String oldUid);

}
