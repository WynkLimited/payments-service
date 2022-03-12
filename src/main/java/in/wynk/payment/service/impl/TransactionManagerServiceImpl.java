package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.event.PaymentSettlementEvent;
import in.wynk.payment.core.event.TransactionSnapshotEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.TransactionDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import in.wynk.subscription.common.enums.SettlementType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionManagerServiceImpl implements ITransactionManagerService {

    private final PaymentCachingService cachingService;
    private final IPurchaseDetailsManger purchaseDetailsManger;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;

    private Transaction upsert(Transaction transaction) {
        Transaction persistedEntity = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).save(transaction);
        final TransactionSnapshotEvent.TransactionSnapshotEventBuilder builder = TransactionSnapshotEvent.builder().transaction(transaction);
        Optional.ofNullable(purchaseDetailsManger.get(transaction)).ifPresent(builder::purchaseDetails);
        applicationEventPublisher.publishEvent(builder.build());
        publishAnalytics(persistedEntity);
        return persistedEntity;
    }

    @Override
    public Transaction get(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(id).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, id));
    }

    private Transaction initTransaction(Transaction txn) {
        Transaction transaction = upsert(txn);
        Session<String, SessionDTO> session = SessionContextHolder.get();
        if (Objects.nonNull(session) && Objects.nonNull(session.getBody())) {
            SessionDTO sessionDTO = session.getBody();
            sessionDTO.put(TRANSACTION_ID, transaction.getIdStr());
            sessionDTO.put(PAYMENT_CODE, transaction.getPaymentChannel().getCode());
        }
        return transaction;
    }

    @Override
    public Transaction init(AbstractTransactionInitRequest transactionInitRequest) {
        final Transaction transaction = PlanTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass()) ? initPlanTransaction((PlanTransactionInitRequest) transactionInitRequest) : initPointTransaction((PointTransactionInitRequest) transactionInitRequest);
        final TransactionDetails.TransactionDetailsBuilder transactionDetailsBuilder = TransactionDetails.builder();
        Optional.ofNullable(purchaseDetailsManger.get(transaction)).ifPresent(transactionDetailsBuilder::purchaseDetails);
        TransactionContext.set(transactionDetailsBuilder.transaction(transaction).build());
        return transaction;
    }

    /**
     * initiate transaction and upsert payer info for plan based charging, skip in case point charging
     **/
    @Override
    public Transaction init(AbstractTransactionInitRequest transactionInitRequest, IPurchaseDetails purchaseDetails) {
        if (PlanTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass())) {
            final Transaction transaction = initPlanTransaction((PlanTransactionInitRequest) transactionInitRequest);
            purchaseDetailsManger.save(transaction, purchaseDetails);
            TransactionContext.set(TransactionDetails.builder().transaction(transaction).purchaseDetails(purchaseDetails).build());
            return transaction;
        }
        return init(transactionInitRequest);
    }

    private Transaction initPlanTransaction(PlanTransactionInitRequest transactionInitRequest) {
        Transaction txn = Transaction.builder().paymentChannel(transactionInitRequest.getPaymentCode().name()).clientAlias(transactionInitRequest.getClientAlias()).type(transactionInitRequest.getEvent().name()).discount(transactionInitRequest.getDiscount()).coupon(transactionInitRequest.getCouponId()).planId(transactionInitRequest.getPlanId()).amount(transactionInitRequest.getAmount()).msisdn(transactionInitRequest.getMsisdn()).status(transactionInitRequest.getStatus()).uid(transactionInitRequest.getUid()).initTime(Calendar.getInstance()).consent(Calendar.getInstance()).build();
        return initTransaction(txn);
    }

    private Transaction initPointTransaction(PointTransactionInitRequest transactionInitRequest) {
        Transaction txn = Transaction.builder().paymentChannel(transactionInitRequest.getPaymentCode().name()).clientAlias(transactionInitRequest.getClientAlias()).type(transactionInitRequest.getEvent().name()).discount(transactionInitRequest.getDiscount()).coupon(transactionInitRequest.getCouponId()).itemId(transactionInitRequest.getItemId()).amount(transactionInitRequest.getAmount()).msisdn(transactionInitRequest.getMsisdn()).status(transactionInitRequest.getStatus()).uid(transactionInitRequest.getUid()).initTime(Calendar.getInstance()).consent(Calendar.getInstance()).build();
        return initTransaction(txn);
    }

    @Override
    public void revision(AbstractTransactionRevisionRequest request) {
        try {
            if (!EnumSet.of(PaymentEvent.POINT_PURCHASE, PaymentEvent.REFUND).contains(request.getTransaction().getType())) {
                if (EnumSet.of(PaymentEvent.UNSUBSCRIBE, PaymentEvent.CANCELLED).contains(request.getTransaction().getType())) {
                    if (request.getExistingTransactionStatus() != TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.SUCCESS) {
                        subscriptionServiceManager.unSubscribePlan(AbstractUnSubscribePlanRequest.from(request));
                    }
                } else {
                    recurringPaymentManagerService.scheduleRecurringPayment(request);
                    if ((request.getExistingTransactionStatus() != TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.SUCCESS) || (request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS && request.getFinalTransactionStatus() == TransactionStatus.MIGRATED)) {
                        subscriptionServiceManager.subscribePlan(AbstractSubscribePlanRequest.from(request));
                        if (StringUtils.isEmpty(request.getTransaction().getItemId()) && cachingService.getPlan(request.getTransaction().getPlanId()).getSettlementType() == SettlementType.SPLIT)
                            applicationEventPublisher.publishEvent(PaymentSettlementEvent.builder().tid(request.getTransactionId()).build());
                    }
                }
            }
        } catch (WynkRuntimeException e) {
            request.getTransaction().setStatus(TransactionStatus.FAILURE.getValue());
            throw e;
        } finally {
            if (request.getTransaction().getStatus() != TransactionStatus.INPROGRESS && request.getTransaction().getStatus() != TransactionStatus.UNKNOWN) {
                request.getTransaction().setExitTime(Calendar.getInstance());
            }
            if (!(request.getExistingTransactionStatus() == TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.FAILURE)) {
                this.upsert(request.getTransaction());
            }
        }
    }

    private void publishAnalytics(Transaction transaction) {
        AnalyticService.update(UID, transaction.getUid());
        AnalyticService.update(MSISDN, transaction.getMsisdn());
        AnalyticService.update(PLAN_ID, transaction.getPlanId());
        AnalyticService.update(ITEM_ID, transaction.getItemId());
        AnalyticService.update(AMOUNT_PAID, transaction.getAmount());
        AnalyticService.update(CLIENT, transaction.getClientAlias());
        AnalyticService.update(COUPON_CODE, transaction.getCoupon());
        AnalyticService.update(TRANSACTION_ID, transaction.getIdStr());
        AnalyticService.update(PAYMENT_EVENT, transaction.getType().getValue());
        AnalyticService.update(TRANSACTION_STATUS, transaction.getStatus().getValue());
        AnalyticService.update(PaymentConstants.PAYMENT_CODE, transaction.getPaymentChannel().getCode());
        AnalyticService.update(PaymentConstants.PAYMENT_METHOD, transaction.getPaymentChannel().getCode());
    }

}