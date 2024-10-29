package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.audit.listener.MongoAuditingListener;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.dao.entity.Coupon;
import in.wynk.coupon.core.dao.entity.CouponCodeLink;
import in.wynk.coupon.core.dao.entity.UserCouponAvailedRecord;
import in.wynk.coupon.core.dao.repository.AvailedCouponsDao;
import in.wynk.coupon.core.service.CouponCachingService;
import in.wynk.coupon.core.service.ICouponCodeLinkService;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.ExternalTransactionReportEvent;
import in.wynk.payment.core.event.PaymentSettlementEvent;
import in.wynk.payment.core.event.PaymentUserDeactivationMigrationEvent;
import in.wynk.payment.core.event.TransactionSnapshotEvent;
import in.wynk.payment.dto.GenerateItemEvent;
import in.wynk.payment.dto.PointDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.TransactionDetails;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.gpbs.request.GooglePlayProductDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.SettlementType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.GOOGLE_IAP;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CODE;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionManagerServiceImpl implements ITransactionManagerService {

    private final PaymentCachingService cachingService;
    private final IPurchaseDetailsManger purchaseDetailsManger;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final ICouponCodeLinkService couponCodeLinkService;
    private final CouponCachingService couponCachingService;
    private final MongoAuditingListener auditingListener;
    private final ApplicationEventPublisher eventPublisher;
    private final IMerchantTransactionService merchantTransactionService;

    @Override
    public Transaction upsert (Transaction transaction) {
        Transaction persistedEntity = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).save(transaction);
        publishAnalytics(persistedEntity);
        return persistedEntity;
    }

    @Override
    public Transaction get (String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(id)
                .orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010, id));
    }

    @Override
    public Set<Transaction> getAll (Set<String> idList) {
        return idList.stream().map(this::get).collect(Collectors.toSet());
    }

    public List<Transaction> saveAll (List<Transaction> transactionsList) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).saveAll(transactionsList);
    }

    //Update new uid in all transactions & update uid in receipt_details
    public void migrateOldTransactions (String userId, String uid, String oldUid, String service) {
        final List<ReceiptDetails> allReceiptDetails =
                RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByUid(oldUid);
        //update new uid in all transactions
        updateTransactions(userId, uid, allReceiptDetails);
        //update new uid in all receipts
        updateReceiptDetails(uid, service, allReceiptDetails);
        //update new uid in availed coupons
        updateCouponData(uid, oldUid);
        applicationEventPublisher.publishEvent(PaymentUserDeactivationMigrationEvent.builder().id(userId).uid(uid).oldUid(oldUid).build());
    }

    private void updateCouponData (String uid, String oldUid) {
        try {
            AvailedCouponsDao availedCouponsRepository = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse("paymentApi"), AvailedCouponsDao.class);
            availedCouponsRepository.findById(oldUid).ifPresent(userCouponAvailedRecord -> availedCouponsRepository
                    .save(UserCouponAvailedRecord.builder()
                            .id(uid)
                            .couponPairs(userCouponAvailedRecord.getCouponPairs())
                            .build()));
            log.info(PaymentLoggingMarker.USER_DEACTIVATION_COUPON_MIGRATION_INFO, "Coupon data migrated from old uid : {} to new uid : {}", oldUid, uid);
        } catch (Exception e) {
            log.info(PaymentLoggingMarker.USER_DEACTIVATION_MIGRATION_ERROR, "Unable to migrate Coupon data from old uid : {} to new uid : {} due to {}", oldUid, uid, e.getMessage());
            throw e;
        }
    }

    private void updateReceiptDetails (String uid, String service, List<ReceiptDetails> allReceiptDetails) {
        try {
            if (!CollectionUtils.isEmpty(allReceiptDetails)) {
                allReceiptDetails.forEach(receiptDetails -> {
                    PlanDTO plan = cachingService.getPlan(receiptDetails.getPlanId());
                    if (plan.getService().equalsIgnoreCase(service)) {
                        receiptDetails.setUid(uid);
                    }
                });
                auditingListener.onBeforeSaveAll(allReceiptDetails);
                RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).saveAll(allReceiptDetails);
                log.info(PaymentLoggingMarker.USER_DEACTIVATION_RECEIPT_MIGRATION_INFO, "Receipt data updated to new uid : {} ", uid);
            }
        } catch (Exception e) {
            log.info(PaymentLoggingMarker.USER_DEACTIVATION_MIGRATION_ERROR, "Unable to update Receipt data to new uid : {} due to {}", uid, e.getMessage());
            throw e;
        }
    }

    private void updateTransactions (String userId, String uid, List<ReceiptDetails> allReceiptDetails) {
        try {
            List<String> transactionIds = purchaseDetailsManger.getByUserId(userId);
            if (Objects.nonNull(transactionIds)) {
                transactionIds.addAll(allReceiptDetails.stream().map(ReceiptDetails::getPaymentTransactionId).collect(Collectors.toList()));
            } else {
                transactionIds = allReceiptDetails.stream().map(ReceiptDetails::getPaymentTransactionId).collect(Collectors.toList());
            }
            final List<Transaction> transactionsList = transactionIds.stream().map(this::get).collect(Collectors.toList());
            transactionsList.forEach(txn -> {
                txn.setUid(uid);
            /*if(txn.getPaymentChannel().name().equalsIgnoreCase(ITUNES)){
                applicationEventPublisher.publishEvent(MigrateITunesReceiptEvent.builder().tid(txn.getIdStr()).build());
            }*/
            });
            saveAll(transactionsList);
            log.info(PaymentLoggingMarker.USER_DEACTIVATION_TRANSACTION_MIGRATION_INFO, "Transaction data updated to new uid : {} for user : {}", uid, userId);
        } catch (Exception e) {
            log.info(PaymentLoggingMarker.USER_DEACTIVATION_MIGRATION_ERROR, "Unable to update transactions data to new uid : {} for user : {} due to {}", uid, userId, e.getMessage());
            throw e;
        }
    }

    private Transaction initTransaction (Transaction txn, String originalTransactionId) {
        Transaction transaction = null;
        try {
            transaction = upsert(txn);
            Session<String, SessionDTO> session = SessionContextHolder.get();
            if (Objects.nonNull(session) && Objects.nonNull(session.getBody())) {
                SessionDTO sessionDTO = session.getBody();
                sessionDTO.put(TRANSACTION_ID, transaction.getIdStr());
                sessionDTO.put(PAYMENT_CODE, transaction.getPaymentChannel().getCode());
            }
            // WCF-5228: Update transaction in renewal table for renewal event when updating in renewal table in the transaction table
            addEntryInRenewalTable(txn, originalTransactionId);
        } catch (Exception ex) {
            throw new WynkRuntimeException("Exception occurred while initiating the transaction", ex);
        } finally {
            if (Objects.nonNull(transaction)) {
                publishTransactionSnapShotEvent(transaction);
            }
        }
        return transaction;
    }

    private void addEntryInRenewalTable (Transaction txn, String originalTransactionId) {
        if (txn.getType() == PaymentEvent.RENEW && txn.getPaymentChannel().isInternalRecurring() && !(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE.equalsIgnoreCase(txn.getPaymentChannel().getCode()))) {
            Integer planId = subscriptionServiceManager.getUpdatedPlanId(txn.getPlanId(), txn.getType());
            PlanDTO planDTO = BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(planId);
            Calendar nextRecurringDateTime = Calendar.getInstance();
            nextRecurringDateTime.setTimeInMillis(System.currentTimeMillis() + planDTO.getPeriod().getTimeUnit().toMillis(planDTO.getPeriod().getValidity()));
            recurringPaymentManagerService.scheduleRecurringPayment(txn.getIdStr(), originalTransactionId, txn.getType(), txn.getPaymentChannel().getCode(), nextRecurringDateTime, 0, txn,
                    TransactionStatus.INPROGRESS, null);
        }
    }

    @Override
    public Transaction init (AbstractTransactionInitRequest transactionInitRequest) {
        PurchaseDetails purchaseDetails = null;
        final Transaction transaction =
                PlanTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass()) ? initPlanTransaction((PlanTransactionInitRequest) transactionInitRequest) :
                        initPointTransaction((PointTransactionInitRequest) transactionInitRequest);
        if (Objects.nonNull(transactionInitRequest.getTxnId())) {
            final String oldTransactionId = transactionInitRequest.getTxnId();
            final Transaction oldTransaction = get(oldTransactionId);
            final IPurchaseDetails details = purchaseDetailsManger.get(oldTransaction);
            if (Objects.nonNull(details)) {
                purchaseDetails = PurchaseDetails.builder()
                        .id(transaction.getIdStr())
                        .appDetails(details.getAppDetails())
                        .userDetails(details.getUserDetails())
                        .sessionDetails(details.getSessionDetails())
                        .productDetails(details.getProductDetails())
                        .geoLocation(details.getGeoLocation())
                        .paymentDetails(details.getPaymentDetails())
                        .pageUrlDetails(((IChargingDetails) details).getPageUrlDetails())
                        .callbackUrl(((IChargingDetails) details).getCallbackDetails().getCallbackUrl())
                        .build();
                purchaseDetailsManger.save(transaction, purchaseDetails);
            }
        }
        final TransactionDetails.TransactionDetailsBuilder transactionDetailsBuilder = TransactionDetails.builder();
        Optional.ofNullable(Objects.isNull(purchaseDetails) ? purchaseDetailsManger.get(transaction) : purchaseDetails).ifPresent(transactionDetailsBuilder::purchaseDetails);
        TransactionContext.set(transactionDetailsBuilder.transaction(transaction).build());
        return transaction;
    }

    @Override
    public Transaction init (AbstractTransactionInitRequest transactionInitRequest, IPurchaseDetails purchaseDetails) {
        if (PlanTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass())) {
            final Transaction transaction = initPlanTransaction((PlanTransactionInitRequest) transactionInitRequest);
            purchaseDetailsManger.save(transaction, purchaseDetails);
            TransactionContext.set(TransactionDetails.builder().transaction(transaction).purchaseDetails(purchaseDetails).build());
            return transaction;
        } else if (PointTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass())) {
            final Transaction transaction = initPointTransaction((PointTransactionInitRequest) transactionInitRequest);
            if(purchaseDetails.getProductDetails() instanceof GooglePlayProductDetails) {
                GooglePlayProductDetails googlePlayProductDetails = ((GooglePlayProductDetails) purchaseDetails.getProductDetails());
                googlePlayProductDetails.setTitle(null);
            }
            purchaseDetailsManger.save(transaction, purchaseDetails);
            TransactionContext.set(TransactionDetails.builder().transaction(transaction).purchaseDetails(purchaseDetails).build());
            return transaction;
        }
        return init(transactionInitRequest);
    }

    @Override
    public Transaction init (AbstractTransactionInitRequest transactionInitRequest, AbstractPaymentChargingRequest request) {
        if (PlanTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass())) {
            final Transaction transaction = initPlanTransaction((PlanTransactionInitRequest) transactionInitRequest);
            purchaseDetailsManger.save(transaction, request);
            TransactionContext.set(TransactionDetails.builder().transaction(transaction).request(request).build());
            return transaction;
        } else if (PointTransactionInitRequest.class.isAssignableFrom(transactionInitRequest.getClass())) {
            final Transaction transaction = initPointTransaction((PointTransactionInitRequest) transactionInitRequest);
            PointDetails productDetails = ((PointDetails) request.getProductDetails());
            productDetails.setTitle(null);
            purchaseDetailsManger.save(transaction, request);
            TransactionContext.set(TransactionDetails.builder().transaction(transaction).request(request).build());
            return transaction;
        }
        return init(transactionInitRequest);
    }

    private Transaction initPlanTransaction (PlanTransactionInitRequest transactionInitRequest) {
        Transaction txn = Transaction.builder().paymentChannel(transactionInitRequest.getPaymentGateway().name()).clientAlias(transactionInitRequest.getClientAlias())
                .type(transactionInitRequest.getEvent().name()).discount(transactionInitRequest.getDiscount()).mandateAmount(transactionInitRequest.getMandateAmount())
                .coupon(transactionInitRequest.getCouponId()).planId(transactionInitRequest.getPlanId()).amount(transactionInitRequest.getAmount()).msisdn(transactionInitRequest.getMsisdn())
                .status(transactionInitRequest.getStatus()).uid(transactionInitRequest.getUid()).initTime(Calendar.getInstance()).consent(Calendar.getInstance()).originalTransactionId(transactionInitRequest.getTxnId()).build();
        return initTransaction(txn, transactionInitRequest.getTxnId());
    }

    private Transaction initPointTransaction (PointTransactionInitRequest transactionInitRequest) {
        Transaction txn = Transaction.builder().paymentChannel(transactionInitRequest.getPaymentGateway().name()).clientAlias(transactionInitRequest.getClientAlias())
                .type(transactionInitRequest.getEvent().name()).discount(transactionInitRequest.getDiscount()).coupon(transactionInitRequest.getCouponId()).itemId(transactionInitRequest.getItemId())
                .amount(transactionInitRequest.getAmount()).msisdn(transactionInitRequest.getMsisdn()).status(transactionInitRequest.getStatus()).uid(transactionInitRequest.getUid())
                .initTime(Calendar.getInstance()).consent(Calendar.getInstance()).originalTransactionId(transactionInitRequest.getTxnId()).build();
        return initTransaction(txn, transactionInitRequest.getTxnId());
    }

    @Override
    public void revision (AbstractTransactionRevisionRequest request) {
        try {
            if (!EnumSet.of(PaymentEvent.POINT_PURCHASE, PaymentEvent.REFUND).contains(request.getTransaction().getType())) {
                if (EnumSet.of(PaymentEvent.UNSUBSCRIBE, PaymentEvent.CANCELLED).contains(request.getTransaction().getType())) {
                    if (request.getExistingTransactionStatus() != TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.SUCCESS) {
                        subscriptionServiceManager.unSubscribePlan(AbstractUnSubscribePlanRequest.from(request));
                    }
                } else {
                    recurringPaymentManagerService.scheduleRecurringPayment(request);
                    if ((request.getExistingTransactionStatus() != TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.SUCCESS) ||
                            (request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS && request.getFinalTransactionStatus() == TransactionStatus.MIGRATED)) {
                        subscriptionServiceManager.subscribePlan(AbstractSubscribePlanRequest.from(request));
                        if (StringUtils.isEmpty(request.getTransaction().getItemId()) && cachingService.getPlan(request.getTransaction().getPlanId()).getSettlementType() == SettlementType.SPLIT) {
                            applicationEventPublisher.publishEvent(PaymentSettlementEvent.builder().tid(request.getOriginalTransactionId()).build());
                        }
                        if (ApsConstant.APS.equals(request.getTransaction().getPaymentChannel().getId()) || PaymentConstants.PAYU.equals(request.getTransaction().getPaymentChannel().getId())) {
                            initiateTransactionReportToMerchant(request.getTransaction());
                        }
                    }
                }
            } else if (PaymentEvent.POINT_PURCHASE == request.getTransaction().getType() && (request.getExistingTransactionStatus() == TransactionStatus.INPROGRESS &&
                    (request.getFinalTransactionStatus() == TransactionStatus.SUCCESS || request.getFinalTransactionStatus() == TransactionStatus.FAILURE))) {
                if (request.getFinalTransactionStatus() == TransactionStatus.SUCCESS &&
                        (ApsConstant.APS.equals(request.getTransaction().getPaymentChannel().getId()) || PaymentConstants.PAYU.equals(request.getTransaction().getPaymentChannel().getId()))) {
                    initiateTransactionReportToMerchant(request.getTransaction());
                }
                publishDataToWynkKafka(request.getTransaction());
            }
        } finally {
            if (request.getTransaction().getStatus() != TransactionStatus.INPROGRESS && request.getTransaction().getStatus() != TransactionStatus.UNKNOWN) {
                request.getTransaction().setExitTime(Calendar.getInstance());
            }
            if (!(request.getExistingTransactionStatus() == TransactionStatus.SUCCESS && request.getFinalTransactionStatus() == TransactionStatus.FAILURE)) {
                this.upsert(request.getTransaction());
            }
            if ((request.getExistingTransactionStatus() != request.getFinalTransactionStatus())) {
                publishTransactionSnapShotEvent(request.getTransaction());
            }
        }
    }

    private void publishTransactionSnapShotEvent (Transaction transaction) {
        final TransactionSnapshotEvent.TransactionSnapshotEventBuilder builder = TransactionSnapshotEvent.builder().transaction(transaction);
        Optional.ofNullable(purchaseDetailsManger.get(transaction)).ifPresent(builder::purchaseDetails);
        applicationEventPublisher.publishEvent(builder.build());
    }

    private void initiateTransactionReportToMerchant (Transaction transaction) {
        try {
            String initialTxnId = null;
            if (transaction.getType() == PaymentEvent.RENEW) {
                PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(transaction.getIdStr());
                initialTxnId = renewal.getInitialTransactionId();
            }
            MerchantTransaction merchantData = merchantTransactionService.getMerchantTransaction((initialTxnId != null) ? initialTxnId : transaction.getIdStr());
            if (Objects.nonNull(merchantData.getExternalTokenReferenceId())) {
                AnalyticService.update(EXTERNAL_TRANSACTION_TOKEN, merchantData.getExternalTokenReferenceId());
                ExternalTransactionReportEvent.ExternalTransactionReportEventBuilder builder =
                        ExternalTransactionReportEvent.builder().transactionId(transaction.getIdStr()).externalTokenReferenceId(merchantData.getExternalTokenReferenceId())
                                .clientAlias(transaction.getClientAlias()).paymentEvent(transaction.getType());
                if (transaction.getType() == PaymentEvent.RENEW) {
                    builder.initialTransactionId(initialTxnId);
                }
                eventPublisher.publishEvent(builder.build());
            }
        } catch (Exception ignored) {
        }
    }

    private void publishDataToWynkKafka (Transaction transaction) {
        GenerateItemEvent event =
                GenerateItemEvent.builder().transactionId(transaction.getIdStr()).itemId(transaction.getItemId()).uid(transaction.getUid()).createdDate(transaction.getInitTime())
                        .updatedDate(Calendar.getInstance()).transactionStatus(transaction.getStatus()).event(transaction.getType()).price(transaction.getAmount()).build();
        eventPublisher.publishEvent(event);
    }

    private void publishAnalytics (Transaction transaction) {
        AnalyticService.update(UID, transaction.getUid());
        AnalyticService.update(MSISDN, transaction.getMsisdn());
        AnalyticService.update(PLAN_ID, transaction.getPlanId());
        AnalyticService.update(ITEM_ID, transaction.getItemId());
        AnalyticService.update(AMOUNT_PAID, transaction.getAmount());
        AnalyticService.update(CLIENT, transaction.getClientAlias());
        AnalyticService.update(COUPON_CODE, transaction.getCoupon());
        AnalyticService.update(TRANSACTION_ID, transaction.getIdStr());
        if (Objects.nonNull(transaction.getCoupon())) {
            String couponCode = transaction.getCoupon();
            CouponCodeLink couponLinkOption = couponCodeLinkService.fetchCouponCodeLink(transaction.getCoupon().toUpperCase(Locale.ROOT));
            if (couponLinkOption != null) {
                Coupon coupon = couponCachingService.get(couponLinkOption.getCouponId());
                if (!coupon.isCaseSensitive()) {
                    couponCode = couponCode.toUpperCase(Locale.ROOT);
                }
            }
            String couponId = BeanLocatorFactory.getBean(ICouponCodeLinkService.class).fetchCouponCodeLink(couponCode).getCouponId();
            Coupon coupon = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<Coupon, String>>() {
            }).get(couponId);
            AnalyticService.update(COUPON_GROUP, coupon.getId());
            AnalyticService.update(DISCOUNT_TYPE, coupon.getDiscountType().toString());
            AnalyticService.update(DISCOUNT_VALUE, coupon.getDiscount());
        }
        AnalyticService.update(PAYMENT_EVENT, transaction.getType().getValue());
        AnalyticService.update(TRANSACTION_STATUS, transaction.getStatus().getValue());
        AnalyticService.update(INIT_TIMESTAMP, transaction.getInitTime().getTime().getTime());
        if (Objects.nonNull(transaction.getExitTime())) {
            AnalyticService.update(EXIT_TIMESTAMP, transaction.getExitTime().getTime().getTime());
        }
        AnalyticService.update(PAYMENT_CODE, transaction.getPaymentChannel().getCode());
        AnalyticService.update(PaymentConstants.PAYMENT_METHOD, transaction.getPaymentChannel().getCode());
    }

}