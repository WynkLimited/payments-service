package in.wynk.payment.service;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dto.Transaction;

import java.util.UUID;

public interface ITransactionManagerService {

    Transaction upsert(Transaction transaction);

    Transaction get(UUID id);

    Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, String wynkService);

}
