package in.wynk.payment.service.impl;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dto.Transaction;
import in.wynk.payment.dao.ITransactionDao;
import in.wynk.payment.service.ITransactionManagerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.UUID;

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
    public Transaction get(UUID id) {
        return transactionDao.findById(id).orElseThrow(()->new WynkRuntimeException(PaymentErrorType.PAY010, "Invalid txnId - "+id.toString()));
    }

    public Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, String wynkService) {
        return upsert(Transaction.builder().planId(planId).amount(amount).initTime(Calendar.getInstance())
                .consent(Calendar.getInstance()).uid(uid).service(wynkService).msisdn(msisdn)
                .paymentChannel(paymentCode.name()).status(TransactionStatus.INPROGRESS.name())
                .type(TransactionEvent.PURCHASE.name()).build());
    }

}
