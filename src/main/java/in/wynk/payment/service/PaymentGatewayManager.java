package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.exception.IWynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.FraudAware;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.event.*;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentAccountDeletionResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.*;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import io.netty.channel.ConnectTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import javax.annotation.PostConstruct;
import java.net.SocketTimeoutException;
import java.util.*;

import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentErrorType.APS007;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY024;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.CHARGING_API_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.RENEWAL_STATUS_ERROR;

@Slf4j
@Service(BeanConstant.PAYMENT_MANAGER_V2)
@RequiredArgsConstructor
public class PaymentGatewayManager
        implements
        IPreDebitNotificationService,
        IPaymentRenewal<PaymentRenewalChargingRequest>,
        IPaymentRefund<AbstractPaymentRefundResponse, PaymentRefundInitRequest>,
        IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest>,
        IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>,
        IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest>,
        IPaymentCallback<CallbackResponseWrapper<? extends AbstractPaymentCallbackResponse>, CallbackRequestWrapperV2<?>>,
        ICancellingRecurringService, IMerchantTDRService, IMerchantIapSubscriptionCancellationService {

    private final ICouponManager couponManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ISqsManagerService<Object> sqsManagerService;
    private final ITransactionManagerService transactionManager;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IMerchantTransactionService merchantTransactionService;
    private final Map<Class<? extends AbstractTransactionStatusRequest>, IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>> statusDelegator = new HashMap<>();
    private final Gson gson;
    private final PaymentGatewayCommon common;
    private final RecurringTransactionUtils recurringTransactionUtils;

    @PostConstruct
    public void init() {
        this.statusDelegator.put(ChargingTransactionStatusRequest.class, new ChargingTransactionStatusService());
        this.statusDelegator.put(AbstractTransactionReconciliationStatusRequest.class, new ReconciliationTransactionStatusService());
    }

    @FraudAware(name = CHARGING_FRAUD_DETECTION_CHAIN)
    public AbstractPaymentChargingResponse charge(AbstractPaymentChargingRequest request) {
        PaymentGateway paymentGateway = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request), request);
        final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargingService =
                BeanLocatorFactory.getBean(paymentGateway.getCode(),
                        new ParameterizedTypeReference<IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>>() {
                        });

        try {
            AbstractPaymentChargingResponse response = chargingService.charge(request);
            if (paymentGateway.isPreDebit()) {
                final TransactionStatus finalStatus = TransactionContext.get().getStatus();
                final TransactionStatus existingStatus = transaction.getStatus();
                transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
                exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            }
            return response;
        } catch (Exception ex) {
            log.error(CHARGING_API_FAILURE, ex.getMessage());
            final PaymentErrorEvent.Builder eventBuilder = PaymentErrorEvent.builder(transaction.getIdStr()).clientAlias(transaction.getClientAlias());
            if (ex instanceof WynkRuntimeException) {
                final WynkRuntimeException original = (WynkRuntimeException) ex;
                final IWynkErrorType errorType = original.getErrorType();
                eventBuilder.code(Objects.nonNull(errorType) ? errorType.getErrorCode() : original.getErrorCode());
                eventBuilder.description(Objects.nonNull(errorType) ? errorType.getErrorMessage() : original.getMessage());
            } else {
                eventBuilder.code(PaymentErrorType.PAY007.getErrorCode()).description(ex.getMessage());
            }
            eventPublisher.publishEvent(eventBuilder.build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY007, ex.getMessage());
        } finally {
            sqsManagerService.publishSQSMessage(
                    PaymentReconciliationMessage.builder().paymentMethodId(request.getPaymentDetails().getPaymentId()).paymentCode(transaction.getPaymentChannel().getId())
                            .paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                            .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
            if (BaseConstants.PLAN.equals(request.getProductDetails().getType())) {
                eventPublisher.publishEvent(PurchaseInitEvent.builder().clientAlias(transaction.getClientAlias()).transactionId(transaction.getIdStr()).uid(transaction.getUid()).msisdn(transaction
                        .getMsisdn()).productDetails(request.getProductDetails()).appDetails(request.getAppDetails()).sid(Optional.ofNullable(SessionContextHolder
                        .getId())).build());
            }
            if (Objects.nonNull(request.getAppStoreDetails()) && Objects.nonNull(request.getAppStoreDetails().getExternalTransactionToken())) {
                final MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
                merchantTransactionEventBuilder.externalTokenReferenceId(request.getAppStoreDetails().getExternalTransactionToken());
                eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
            }
        }
    }

    @Override
    public AbstractVerificationResponse verify(AbstractVerificationRequest request) {
        String paymentCode = request.getPaymentCode().getCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.toUpperCase());
        final IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> verifyService =
                BeanLocatorFactory.getBean(paymentCode, new ParameterizedTypeReference<IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest>>() {
                });
        return verifyService.verify(request);
    }

    @Override
    public AbstractPaymentAccountDeletionResponse delete(AbstractPaymentAccountDeletionRequest request) {
        String paymentCode = request.getPaymentCode().getCode();
        AnalyticService.update(PAYMENT_METHOD, paymentCode.toUpperCase());
        final IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest> deleteService =
                BeanLocatorFactory.getBean(paymentCode, new ParameterizedTypeReference<IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest>>() {
                });
        return deleteService.delete(request);
    }

    private void exhaustCouponIfApplicable(TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        if (existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            if (StringUtils.isNotEmpty(transaction.getCoupon())) {
                try {
                    couponManager.exhaustCoupon(transaction.getUid(), transaction.getCoupon());
                } catch (WynkRuntimeException e) {
                    log.error(e.getMarker(), e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
        IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> delegatorStatusService;
        if (AbstractTransactionReconciliationStatusRequest.class.isAssignableFrom(request.getClass())) {
            delegatorStatusService = statusDelegator.get(AbstractTransactionReconciliationStatusRequest.class);
        } else {
            delegatorStatusService = statusDelegator.get(request.getClass());
        }
        if (Objects.isNull(delegatorStatusService)) {
            throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return delegatorStatusService.reconcile(request);
    }

    @Override
    @TransactionAware(txnId = "#request.transactionId")
    public CallbackResponseWrapper<AbstractPaymentCallbackResponse> handle(CallbackRequestWrapperV2<?> request) {
        final PaymentGateway pg = request.getPaymentGateway();
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest> callbackService =
                BeanLocatorFactory.getBean(pg.getCode(), new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest>>() {
                });
        try {
            final AbstractPaymentCallbackResponse response = callbackService.handle(request.getBody());
            if (pg.isPreDebit() && Objects.nonNull(response)) {
                eventPublisher.publishEvent(
                        PaymentErrorEvent.builder(transaction.getIdStr()).code(PaymentErrorType.PAY302.getErrorCode()).description(PaymentErrorType.PAY302.getErrorMessage()).build());
            }
            return CallbackResponseWrapper.builder().callbackResponse(response).transaction(transaction).build();
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(e.getErrorCode(), e.getMessage(), e.getErrorTitle());
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).lastSuccessTransactionId(lastSuccessTransactionId).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
        }
    }

    private String getLastSuccessTransactionId (Transaction transaction) {
        if (transaction.getType() == PaymentEvent.RENEW) {
            PaymentRenewal renewal =
                    RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class).findById(transaction.getIdStr())
                            .orElse(null);
            return Objects.nonNull(renewal) ? renewal.getLastSuccessTransactionId() : null;
        }
        return null;
    }

    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<Void> handleNotification(NotificationRequest request) {
        final IReceiptDetailService<?, IAPNotification> receiptDetailService =
                BeanLocatorFactory.getBean(request.getPaymentGateway().getCode(), new ParameterizedTypeReference<IReceiptDetailService<?, IAPNotification>>() {
                });
        DecodedNotificationWrapper<IAPNotification> wrapper = receiptDetailService.isNotificationEligible(request.getPayload());
        AnalyticService.update(wrapper.getDecodedNotification());
        if (wrapper.isEligible()) {
            final UserPlanMapping<?> mapping = receiptDetailService.getUserPlanMapping(wrapper);
            if (mapping != null) {
                String productType = Objects.nonNull(mapping.getItemId()) ? BaseConstants.POINT : BaseConstants.PLAN;
                final in.wynk.common.enums.PaymentEvent event = receiptDetailService.getPaymentEvent(wrapper, productType);
                final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(
                        PlanRenewalRequest.builder().txnId(mapping.getLinkedTransactionId()).planId(mapping.getPlanId()).uid(mapping.getUid()).msisdn(mapping.getMsisdn()).paymentGateway(request.getPaymentGateway())
                                .clientAlias(request.getClientAlias()).build());
                transactionInitRequest.setEvent(event);
                final Transaction transaction = transactionManager.init(transactionInitRequest);
                handleNotification(transaction, mapping);
                return WynkResponseEntity.<Void>builder().success(true).build();
            }
        }
        return WynkResponseEntity.<Void>builder().success(false).build();
    }

    private <T> void handleNotification(Transaction transaction, UserPlanMapping<T> mapping) {
        final TransactionStatus existingStatus = transaction.getStatus();
        final IPaymentNotificationService<T> notificationService =
                BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), new ParameterizedTypeReference<IPaymentNotificationService<T>>() {
                });
        try {
            notificationService.handleNotification(transaction, mapping);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            TransactionStatus finalStatus = TransactionContext.get().getStatus();
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).lastSuccessTransactionId(lastSuccessTransactionId).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            // removing old transaction from renewal table, if we keep it then its renewal will bound to fail due to receipt is linked with new transaction id
            if (transaction.getStatus() == TransactionStatus.SUCCESS && !StringUtils.isEmpty(mapping.getLinkedTransactionId()))
                eventPublisher.publishEvent(UnScheduleRecurringPaymentEvent.builder().transactionId(mapping.getLinkedTransactionId()).clientAlias(transaction.getClientAlias()).reason("Transaction id " + transaction.getIdStr() + " is scheduled in renewal via payment callback therefore old transaction " + mapping.getLinkedTransactionId() + " is not required").build());
        }
    }

    @Override
    public void renew(PaymentRenewalChargingRequest request) {
        PaymentGateway paymentGateway = request.getPaymentGateway();
        final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(
                PlanRenewalRequest.builder().planId(request.getPlanId()).txnId(request.getId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentGateway(paymentGateway)
                        .clientAlias(request.getClientAlias()).build());
        final Transaction transaction = transactionManager.init(transactionInitRequest);
        final TransactionStatus initialStatus = transaction.getStatus();
        final IPaymentRenewal<PaymentRenewalChargingRequest> renewalService =
                BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(),
                        new ParameterizedTypeReference<IPaymentRenewal<PaymentRenewalChargingRequest>>() {
                        });
        final MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            renewalService.renew(request);
        } catch (RestClientException e) {
            PaymentErrorEvent.Builder errorEventBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
            if (e.getRootCause() != null) {
                if (e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException) {
                    log.error(RENEWAL_STATUS_ERROR, "Socket timeout but valid for reconciliation for request : due to {}", e.getMessage(), e);
                    errorEventBuilder.code(APS007.getErrorCode());
                    errorEventBuilder.description(APS007.getErrorMessage() + "for " + paymentGateway);
                    eventPublisher.publishEvent(errorEventBuilder.build());
                    throw new WynkRuntimeException(APS007);
                } else {
                    handleException(errorEventBuilder, paymentGateway, e, transaction);
                }
            } else {
                handleException(errorEventBuilder, paymentGateway, e, transaction);
            }
        } catch (Exception ex) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            PaymentErrorEvent.Builder errorEventBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
            if (ex instanceof WynkRuntimeException) {
                final WynkRuntimeException original = (WynkRuntimeException) ex;
                final IWynkErrorType errorType = original.getErrorType();
                errorEventBuilder.code(Objects.nonNull(errorType) ? errorType.getErrorCode() : original.getErrorCode());
                errorEventBuilder.description(Objects.nonNull(errorType) ? errorType.getErrorMessage() : original.getMessage());
                PaymentErrorEvent errorEvent = errorEventBuilder.build();
                recurringTransactionUtils.cancelRenewalBasedOnErrorReason(errorEvent.getDescription(), transaction);
                eventPublisher.publishEvent(errorEvent);
                throw ex;
            } else {
                errorEventBuilder.code(PaymentErrorType.PAY024.getErrorCode()).description(PaymentErrorType.PAY024.getErrorMessage());
                eventPublisher.publishEvent(errorEventBuilder.build());
                throw new WynkRuntimeException(PAY024, ex);
            }
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
            if (renewalService.canRenewalReconciliation()) {
                sqsManagerService.publishSQSMessage(
                        PaymentReconciliationMessage.builder().paymentMethodId(common.getPaymentId(transactionManager.get(request.getId()))).paymentCode(transaction.getPaymentChannel().getId())
                                .paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                                .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid())
                                .originalAttemptSequence(request.getAttemptSequence() + 1).originalTransactionId(request.getId()).build());
            }
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(initialStatus).finalTransactionStatus(finalStatus)
                    .attemptSequence(request.getAttemptSequence()).originalTransactionId(request.getId()).lastSuccessTransactionId(transaction.getIdStr()).build());
        }
    }

    private void handleException(PaymentErrorEvent.Builder errorEventBuilder, PaymentGateway paymentGateway, RestClientException e, Transaction transaction) {
        transaction.setStatus(TransactionStatus.FAILURE.getValue());
        errorEventBuilder.code(PAY024.getErrorCode());
        errorEventBuilder.description(PAY024.getErrorMessage() + "for " + paymentGateway.getCode());
        eventPublisher.publishEvent(errorEventBuilder.build());
        throw new WynkRuntimeException(PAY024, e);
    }

    @Override
    public AbstractPreDebitNotificationResponse notify(PreDebitNotificationMessage message) {
        log.info(PaymentLoggingMarker.PRE_DEBIT_NOTIFICATION_QUEUE, "processing PreDebitNotificationMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        AbstractPreDebitNotificationResponse preDebitResponse = BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), IPreDebitNotificationService.class).notify(message);
        AnalyticService.update(ApsConstant.PRE_DEBIT_SI, gson.toJson(preDebitResponse));
        return preDebitResponse;
    }

    @Override
    public void cancelRecurring(String transactionId) {
        BeanLocatorFactory.getBean(transactionManager.get(transactionId).getPaymentChannel().getCode(), ICancellingRecurringService.class)
                .cancelRecurring(transactionId);

    }

    @Override
    @TransactionAware(txnId = "#transactionId", lock = false)
    public BaseTDRResponse getTDR(String transactionId) {
        final Transaction transaction = TransactionContext.get();
        return BeanLocatorFactory.getBeanOrDefault(transaction.getPaymentChannel().getCode(), IMerchantTDRService.class, nope -> BaseTDRResponse.from(-1)).getTDR(transactionId);
    }

    @Override
    @TransactionAware(txnId = "#transactionId", lock = false)
    public void cancelSubscription (String uid, String transactionId) {
        BeanLocatorFactory.getBean(TransactionContext.get().getPaymentChannel().getCode(), IMerchantIapSubscriptionCancellationService.class).cancelSubscription(uid, transactionId);
    }

    private static class ChargingTransactionStatusService implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        @TransactionAware(txnId = "#request.transactionId", lock = false)
        public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            final TransactionStatus txnStatus = transaction.getStatus();
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(txnStatus).transactionType(transaction.getType()).build();
        }
    }

    private class ReconciliationTransactionStatusService implements IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        @TransactionAware(txnId = "#request.transactionId")
        public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            final TransactionStatus existingStatus = transaction.getStatus();
            try {
                final PaymentGateway paymentGateway = transaction.getPaymentChannel();
                final IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> statusService =
                        BeanLocatorFactory.getBean(paymentGateway.getCode(),
                                new ParameterizedTypeReference<IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>() {
                                });
                return statusService.reconcile(request);
            } finally {
                final TransactionStatus finalStatus = transaction.getStatus();
                AnalyticService.update(PAYMENT_METHOD, transaction.getPaymentChannel().name());
                AsyncTransactionRevisionRequest.AsyncTransactionRevisionRequestBuilder<?, ?> builder =
                        AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus);
                if (transaction.getType() == PaymentEvent.RENEW) {
                    RenewalChargingTransactionReconciliationStatusRequest renewalChargingTransactionReconciliationStatusRequest = (RenewalChargingTransactionReconciliationStatusRequest) request;
                    builder.attemptSequence(renewalChargingTransactionReconciliationStatusRequest.getOriginalAttemptSequence())
                            .originalTransactionId(renewalChargingTransactionReconciliationStatusRequest.getOriginalTransactionId());
                }
                reviseTransactionAndExhaustCoupon(transaction, existingStatus, builder.build());
                publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
            }
        }
    }

    @Override
    public AbstractPaymentRefundResponse doRefund(PaymentRefundInitRequest request) {
        final Transaction originalTransaction = transactionManager.get(request.getOriginalTransactionId());
        final String externalReferenceId = merchantTransactionService.getPartnerReferenceId(request.getOriginalTransactionId());
        final Transaction refundTransaction =
                transactionManager.init(DefaultTransactionInitRequestMapper.from(
                        RefundTransactionRequestWrapper.builder().request(request).txnId(originalTransaction.getIdStr()).originalTransaction(originalTransaction).build()));
        final TransactionStatus initialStatus = refundTransaction.getStatus();
        final AbstractPaymentRefundRequest refundRequest = AbstractPaymentRefundRequest.from(originalTransaction, externalReferenceId, request.getReason());
        IPaymentRefund<AbstractPaymentRefundResponse, AbstractPaymentRefundRequest> refundService = BeanLocatorFactory.getBean(refundTransaction.getPaymentChannel().getCode(),
                new ParameterizedTypeReference<IPaymentRefund<AbstractPaymentRefundResponse, AbstractPaymentRefundRequest>>() {
                });
        AbstractPaymentRefundResponse refundInitResponse = null;
        try {
            refundInitResponse = refundService.doRefund(refundRequest);
            return refundInitResponse;
        } catch (WynkRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY020, e);
        } finally {
            assert refundInitResponse != null;
            if (ApsConstant.AIRTEL_PAY_STACK.equalsIgnoreCase(originalTransaction.getPaymentChannel().getCode()) ||
                    BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE.equalsIgnoreCase(originalTransaction.getPaymentChannel().getCode()) &&
                            refundInitResponse.getTransactionStatus() != TransactionStatus.FAILURE) {
                sqsManagerService.publishSQSMessage(
                        PaymentReconciliationMessage.builder().paymentMethodId(common.getPaymentId(transactionManager.get(request.getOriginalTransactionId())))
                                .paymentCode(refundTransaction.getPaymentChannel().getId()).extTxnId(refundInitResponse.getExternalReferenceId())
                                .transactionId(refundTransaction.getIdStr()).paymentEvent(refundTransaction.getType()).itemId(refundTransaction.getItemId()).planId(refundTransaction.getPlanId())
                                .msisdn(refundTransaction.getMsisdn()).uid(refundTransaction.getUid()).build());
            }
        }
    }

    private void publishEventsOnReconcileCompletion(TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(in.wynk.common.enums.PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

    private void reviseTransactionAndExhaustCoupon(Transaction transaction, TransactionStatus existingStatus,
                                                   AbstractTransactionRevisionRequest abstractTransactionRevisionRequest) {
        if (transaction.getType() == PaymentEvent.RENEW) {
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            abstractTransactionRevisionRequest.setLastSuccessTransactionId(lastSuccessTransactionId);
        }
        transactionManager.revision(abstractTransactionRevisionRequest);
        exhaustCouponIfApplicable(existingStatus, transaction.getStatus(), transaction);
    }
}
