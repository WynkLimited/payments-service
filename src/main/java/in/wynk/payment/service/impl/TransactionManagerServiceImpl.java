package in.wynk.payment.service.impl;

import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Service
public class TransactionManagerServiceImpl implements ITransactionManagerService {

    private final ITransactionDao transactionDao;
    private final PaymentCachingService cachingService;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;

    public TransactionManagerServiceImpl(@Qualifier(BeanConstant.TRANSACTION_DAO) ITransactionDao transactionDao, PaymentCachingService cachingService, ISubscriptionServiceManager subscriptionServiceManager, IRecurringPaymentManagerService recurringPaymentManagerService) {
        this.transactionDao = transactionDao;
        this.cachingService = cachingService;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
    }

    @Override
    public Transaction upsert(Transaction transaction) {
        return transactionDao.save(transaction);
    }


    @Override
    public Transaction get(String id) {
        return transactionDao.findById(id).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, "Invalid txnId - " + id));
    }

    @Override
    public Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, TransactionEvent event) {
        Transaction transaction = upsert(Transaction.builder().planId(planId).amount(amount).initTime(Calendar.getInstance())
                .consent(Calendar.getInstance()).uid(uid).msisdn(msisdn)
                .paymentChannel(paymentCode.name()).status(TransactionStatus.INPROGRESS.name())
                .type(event.name()).build());
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        if (Objects.nonNull(sessionDTO)) {
            sessionDTO.put(SessionKeys.WYNK_TRANSACTION_ID, transaction.getIdStr());
        }
        return transaction;
    }

    @Override
    public void updateAndPublishSync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn) {
        this.updateAndPublish(transaction, fetchAndUpdateFromSourceFn, true);
    }

    @Override
    public void updateAndPublishAsync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn) {
        this.updateAndPublish(transaction, fetchAndUpdateFromSourceFn, false);
    }

    private void updateAndPublish(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn, boolean isSync) {
        try {
            PlanDTO selectedPlan = cachingService.getPlan(transaction.getPlanId());
            TransactionStatus existingTransactionStatus = transaction.getStatus();
            fetchAndUpdateFromSourceFn.accept(transaction);
            TransactionStatus finalTransactionStatus = transaction.getStatus();

            if (existingTransactionStatus != TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.SUCCESS) {
                if (transaction.getType() == TransactionEvent.SUBSCRIBE) {
                    Calendar nextRecurringDateTime = Calendar.getInstance();
                    nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, selectedPlan.getPeriod().getValidity());
                    recurringPaymentManagerService.scheduleRecurringPayment(transaction.getIdStr(), nextRecurringDateTime);
                }

                if (isSync) {
                    subscriptionServiceManager.subscribePlanSync(transaction.getPlanId(), SessionContextHolder.getId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus, transaction.getType());
                } else {
                    subscriptionServiceManager.subscribePlanAsync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus, transaction.getType());
                }

            } else if (existingTransactionStatus == TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.FAILURE) {
                if (isSync) {
                    subscriptionServiceManager.unSubscribePlanSync(transaction.getPlanId(), SessionContextHolder.getId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus);
                } else {
                    subscriptionServiceManager.unSubscribePlanAsync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus);
                }

            }
        } finally {
            if (transaction.getStatus() != TransactionStatus.INPROGRESS && transaction.getStatus() != TransactionStatus.UNKNOWN) {
                transaction.setExitTime(Calendar.getInstance());
            }
            this.upsert(transaction);
        }
    }

}
