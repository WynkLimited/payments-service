package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.dto.request.TransactionInitRequest;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Consumer;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
@Service
public class TransactionManagerServiceImpl implements ITransactionManagerService {

    private final ITransactionDao transactionDao;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;

    public TransactionManagerServiceImpl(@Qualifier(BeanConstant.TRANSACTION_DAO) ITransactionDao transactionDao, ISubscriptionServiceManager subscriptionServiceManager, IRecurringPaymentManagerService recurringPaymentManagerService) {
        this.transactionDao = transactionDao;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
    }

    @Override
    public Transaction upsert(Transaction transaction) {
        Transaction persistedEntity = transactionDao.save(transaction);
        publishAnalytics(persistedEntity);
        return persistedEntity;
    }

    @Override
    public Transaction get(String id) {
        return transactionDao.findById(id).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, "Invalid txnId - " + id));
    }

    @Override
    public Transaction initiateTransaction(String uid, String msisdn, int planId, Double amount, PaymentCode paymentCode, PaymentEvent event) {
        log.info("Initiating transaction for uid: {}, planId: {}, amount: {}, paymentCode:{}, txnEvent: {}", uid, planId, amount, paymentCode.getCode(), event.getValue());
        Transaction txn = Transaction.builder().planId(planId).amount(amount).initTime(Calendar.getInstance())
                .consent(Calendar.getInstance()).uid(uid).msisdn(msisdn)
                .paymentChannel(paymentCode.name()).status(TransactionStatus.INPROGRESS.name())
                .type(event.name()).build();
        return initTransaction(txn);
    }

    private Transaction initTransaction(Transaction txn) {
        Transaction transaction = upsert(txn);
        Session<SessionDTO> session = SessionContextHolder.get();
        if (Objects.nonNull(session) && Objects.nonNull(session.getBody())) {
            SessionDTO sessionDTO = session.getBody();
            sessionDTO.put(SessionKeys.TRANSACTION_ID, transaction.getIdStr());
            sessionDTO.put(SessionKeys.PAYMENT_CODE, transaction.getPaymentChannel().getCode());
        }
        return transaction;
    }

    @Override
    public Transaction initiateTransaction(TransactionInitRequest transactionInitRequest) {
        Transaction txn = Transaction.builder()
                .clientAlias(transactionInitRequest.getClientAlias())
                .planId(transactionInitRequest.getPlanId())
                .amount(transactionInitRequest.getAmount())
                .initTime(Calendar.getInstance())
                .consent(Calendar.getInstance())
                .uid(transactionInitRequest.getUid())
                .itemId(transactionInitRequest.getItemId())
                .msisdn(transactionInitRequest.getMsisdn())
                .paymentChannel(transactionInitRequest.getPaymentCode().name())
                .status(transactionInitRequest.getStatus())
                .type(transactionInitRequest.getEvent().name())
                .coupon(transactionInitRequest.getCouponId())
                .discount(transactionInitRequest.getDiscount())
                .build();
        return initTransaction(txn);
    }

    @Override
    public void updateAndPublishSync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn) {
        this.updateAndPublish(transaction, fetchAndUpdateFromSourceFn, true);
    }

    @Override
    public void updateAndPublishAsync(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn) {
        this.updateAndPublish(transaction, fetchAndUpdateFromSourceFn, false);
    }

    @Override
    public void updateAndSyncPublish(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus) {
        this.updateAndPublish(transaction, existingTransactionStatus, finalTransactionStatus, true);
    }

    @Override
    public void updateAndAsyncPublish(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus) {
        this.updateAndPublish(transaction, existingTransactionStatus, finalTransactionStatus, false);
    }

    private void updateAndPublish(Transaction transaction, Consumer<Transaction> fetchAndUpdateFromSourceFn, boolean isSync) {
            TransactionStatus existingTransactionStatus = transaction.getStatus();
            fetchAndUpdateFromSourceFn.accept(transaction);
            TransactionStatus finalTransactionStatus = transaction.getStatus();
            updateAndPublish(transaction, existingTransactionStatus, finalTransactionStatus, isSync);
    }

    private void updateAndPublish(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus, boolean isSync){
        try {
            if (!EnumSet.of(PaymentEvent.POINT_PURCHASE, PaymentEvent.REFUND).contains(transaction.getType())) {
                if (existingTransactionStatus == TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.FAILURE) {
                    unsubscribePlan(transaction, finalTransactionStatus, isSync);
                } else {
                    recurringPaymentManagerService.scheduleRecurringPayment(transaction, existingTransactionStatus, finalTransactionStatus);
                    if ((existingTransactionStatus != TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.SUCCESS) ||
                            (existingTransactionStatus == TransactionStatus.INPROGRESS && finalTransactionStatus == TransactionStatus.MIGRATED)) {
                        subscribePlan(transaction, finalTransactionStatus, isSync);
                    }
                }
            }
        } catch (WynkRuntimeException e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw e;
        } finally {
            if (transaction.getStatus() != TransactionStatus.INPROGRESS && transaction.getStatus() != TransactionStatus.UNKNOWN) {
                transaction.setExitTime(Calendar.getInstance());
            }
            this.upsert(transaction);
        }
    }

    private void subscribePlan(Transaction transaction, TransactionStatus finalTransactionStatus, boolean isSync) {
        if (isSync) {
            try {
                subscriptionServiceManager.subscribePlanSync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), transaction.getPaymentChannel().getCode(), finalTransactionStatus, transaction.getType());
            } catch (Exception e) {
                subscriptionServiceManager.subscribePlanAsync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), transaction.getPaymentChannel().getCode(), finalTransactionStatus, transaction.getType());
            }
        } else {
            subscriptionServiceManager.subscribePlanAsync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), transaction.getPaymentChannel().getCode(), finalTransactionStatus, transaction.getType());
        }
    }

    private void unsubscribePlan(Transaction transaction, TransactionStatus finalTransactionStatus, boolean isSync) {
        if (isSync) {
            try {
                subscriptionServiceManager.unSubscribePlanSync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus, transaction.getType());
            } catch (Exception e) {
                subscriptionServiceManager.unSubscribePlanAsync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus, transaction.getType());
            }
        } else {
            subscriptionServiceManager.unSubscribePlanAsync(transaction.getPlanId(), transaction.getId().toString(), transaction.getUid(), transaction.getMsisdn(), finalTransactionStatus, transaction.getType());
        }
    }

    private void publishAnalytics(Transaction transaction) {
        AnalyticService.update(UID, transaction.getUid());
        AnalyticService.update(MSISDN, transaction.getMsisdn());
        AnalyticService.update(CLIENT, transaction.getClientAlias());
        AnalyticService.update(TRANSACTION_ID, transaction.getIdStr());
        AnalyticService.update(PAYMENT_EVENT, transaction.getType().getValue());
        AnalyticService.update(TRANSACTION_STATUS, transaction.getStatus().getValue());
        AnalyticService.update(PLAN_ID, transaction.getPlanId());
        AnalyticService.update(ITEM_ID, transaction.getItemId());
        AnalyticService.update(PaymentConstants.PAYMENT_METHOD, transaction.getPaymentChannel().getCode());
    }

}
