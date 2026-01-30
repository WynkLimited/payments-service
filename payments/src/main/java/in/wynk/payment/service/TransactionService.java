package in.wynk.payment.service;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {

    private final ITransactionDao transactionDao;

    public TransactionService(@Qualifier(BeanConstant.TRANSACTION_DAO)ITransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    public List<Transaction> getLatestTransactions(String uid, String clientAlias, int limit) {
        return transactionDao.findAllByUidAndClientAliasOrderByUpdatedAtDesc(
                uid,
                clientAlias,
                PageRequest.of(0, limit)
        );
    }
}
