package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.service.ITransactionManagerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service(BeanConstant.TRANSACTION_MANAGER_SERVICE)
public class TransactionManagerServiceImpl implements ITransactionManagerService {

    private final ITransactionDao transactionDao;

    public TransactionManagerServiceImpl(@Qualifier(BeanConstant.TRANSACTION_DAO) ITransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    @Override
    public Transaction upsert(Transaction transaction) {
        return transactionDao.save(transaction);
    }

    @Override
    public Optional<Transaction> get(String id) {
        return transactionDao.findById(id);
    }

}
