package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentMysqlDbDump;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.service.IPaymentMysqlDbDumpService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
@Service
public class PaymentMysqlDbDumpService implements IPaymentMysqlDbDumpService {


    private final ITransactionDao transactionDao;

    private List<Transaction> transactions;

    public PaymentMysqlDbDumpService(@Qualifier(BeanConstant.TRANSACTION_DAO)ITransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    @Override
    public PaymentMysqlDbDump populatePaymentDbDump(Date fromDate) {
        return PaymentMysqlDbDump.builder().transactions(transactionDao.getTransactionWeeklyDump(fromDate)).build();
    }
}
