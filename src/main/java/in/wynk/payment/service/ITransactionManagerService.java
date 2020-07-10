package in.wynk.payment.service;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Transaction get(String id);

    Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, String wynkService);

}
