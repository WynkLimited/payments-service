package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentDbDump;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.service.IMysqlDbDumpService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
@Service
public class MysqlDbDumpService implements IMysqlDbDumpService {


    private final ITransactionDao transactionDao;

    private List<Transaction> transactions;

    public MysqlDbDumpService(@Qualifier(BeanConstant.TRANSACTION_DAO)ITransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    @Override
    public PaymentDbDump populatePaymentDbDump(Date fromDate, Date toDate) {
        return PaymentDbDump.builder().transactions(transactionDao.getTransactionWeeklyDump(fromDate,toDate)).build();
    }
}
