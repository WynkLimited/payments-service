package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.AbstractErrorDetails;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.IHandler;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.ClientCallbackEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.MIGRATED;
import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.BeanConstant.VERIFY_IAP_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.TXN_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentManager implements IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>, IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequestWrapper<?>>, IMerchantPaymentRefundService<AbstractPaymentRefundResponse, PaymentRefundInitRequest>, IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionReconciliationStatusRequest>, IWalletTopUpService<WalletTopUpResponse, WalletTopUpRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ISqsManagerService<Object> sqsManagerService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;

    @TransactionAware(txnId = "#request.originalTransactionId")
    public WynkResponseEntity<AbstractPaymentRefundResponse> refund(PaymentRefundInitRequest request) {
        final Transaction originalTransaction = TransactionContext.get();
        try {
            final String externalReferenceId = merchantTransactionService.getPartnerReferenceId(request.getOriginalTransactionId());
            final Transaction refundTransaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(RefundTransactionRequestWrapper.builder().request(request).originalTransaction(originalTransaction).build()));
            final IMerchantPaymentRefundService<AbstractPaymentRefundResponse, AbstractPaymentRefundRequest> refundService = BeanLocatorFactory.getBean(refundTransaction.getPaymentChannel().getCode(), new ParameterizedTypeReference<IMerchantPaymentRefundService<AbstractPaymentRefundResponse, AbstractPaymentRefundRequest>>() {
            });
            final AbstractPaymentRefundRequest refundRequest = AbstractPaymentRefundRequest.from(originalTransaction, externalReferenceId, request.getReason());
            final WynkResponseEntity<AbstractPaymentRefundResponse> refundInitResponse = refundService.refund(refundRequest);
            if (Objects.nonNull(refundInitResponse.getBody())) {
                final AbstractPaymentRefundResponse refundResponse = refundInitResponse.getBody().getData();
                if (refundResponse.getTransactionStatus() != TransactionStatus.FAILURE) {
                    sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(refundTransaction.getPaymentChannel()).extTxnId(refundResponse.getExternalReferenceId()).transactionId(refundTransaction.getIdStr()).paymentEvent(refundTransaction.getType()).itemId(refundTransaction.getItemId()).planId(refundTransaction.getPlanId()).msisdn(refundTransaction.getMsisdn()).uid(refundTransaction.getUid()).build());
                }
            }
            return refundInitResponse;
        } catch (WynkRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY020, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingResponse> charge(AbstractChargingRequest<?> request) {
        BeanLocatorFactory.getBean(CHARGING_FRAUD_DETECTION_CHAIN, IHandler.class).handle(request);
        final PaymentCode paymentCode = paymentMethodCache.get(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request.getPurchaseDetails()), request.getPurchaseDetails());
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>> chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>>() {
        });
        try {
            final WynkResponseEntity<AbstractChargingResponse> response = chargingService.charge(request);
            if (paymentCode.isPreDebit()) {
                final WynkResponseEntity.WynkBaseResponse<?, ?> body = response.getBody();
                if (Objects.nonNull(body) && !body.isSuccess()) {
                    final AbstractErrorDetails errorDetails = (AbstractErrorDetails) body.getError();
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorDetails.getCode()).description(errorDetails.getDescription()).build());
                }
                final TransactionStatus finalStatus = TransactionContext.get().getStatus();
                transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
                exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            }
            return response;
        } finally {
            sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        }
    }

    @Override
    @TransactionAware(txnId = "#request.transactionId")
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(CallbackRequestWrapper<?> request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest> callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest>>() {
        });
        try {
            final WynkResponseEntity<AbstractCallbackResponse> response = callbackService.handleCallback(request.getBody());
            if (paymentCode.isPreDebit()) {
                final WynkResponseEntity.WynkBaseResponse<?, ?> body = response.getBody();
                if (Objects.nonNull(body) && !body.isSuccess()) {
                    final AbstractErrorDetails errorDetails = (AbstractErrorDetails) body.getError();
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorDetails.getCode()).description(errorDetails.getDescription()).build());
                }
            }
            return response;
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            final TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
        }
    }

    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<Void> handleNotification(NotificationRequest request) {
        final IReceiptDetailService<?, IAPNotification> receiptDetailService = BeanLocatorFactory.getBean(request.getPaymentCode().getCode(), new ParameterizedTypeReference<IReceiptDetailService<?, IAPNotification>>() {
        });
        DecodedNotificationWrapper<IAPNotification> wrapper = receiptDetailService.isNotificationEligible(request.getPayload());
        AnalyticService.update(wrapper.getDecodedNotification());
        if (wrapper.isEligible()) {
            final UserPlanMapping<?> mapping = receiptDetailService.getUserPlanMapping(wrapper);
            final PaymentEvent event = receiptDetailService.getPaymentEvent(wrapper);
            final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(PlanRenewalRequest.builder().planId(mapping.getPlanId()).uid(mapping.getUid()).msisdn(mapping.getMsisdn()).paymentCode(request.getPaymentCode()).clientAlias(request.getClientAlias()).build());
            transactionInitRequest.setEvent(event);
            final Transaction transaction = transactionManager.init(transactionInitRequest);
            handleNotification(transaction, mapping);
            return WynkResponseEntity.<Void>builder().success(true).build();
        }
        return WynkResponseEntity.<Void>builder().success(false).build();
    }

    private <T> void handleNotification(Transaction transaction, UserPlanMapping<T> mapping) {
        final TransactionStatus existingStatus = transaction.getStatus();
        final IPaymentNotificationService<T> notificationService = BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), new ParameterizedTypeReference<IPaymentNotificationService<T>>() {
        });
        try {
            notificationService.handleNotification(transaction, mapping);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
        }
    }

    @TransactionAware(txnId = "#transactionId", lock = false)
    public WynkResponseEntity<AbstractChargingStatusResponse> status(String transactionId) {
        final Transaction transaction = TransactionContext.get();
        return internalStatus(ChargingTransactionStatusRequest.builder().transactionId(transactionId).planId(transaction.getPlanId()).build());
    }

    @Override
    @TransactionAware(txnId = "#request.transactionId")
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        try {
            return internalStatus(request);
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            AnalyticService.update(PAYMENT_METHOD, transaction.getPaymentChannel().name());
            transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
        }
    }

    public BaseResponse<?> doVerify(VerificationRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final IMerchantVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantVerificationService.class);
        return verificationService.doVerify(request);
    }

    @ClientAware(clientId = "#clientId")
    public BaseResponse<?> doVerifyIap(String clientId, IapVerificationRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantIapPaymentVerificationService.class);
        final LatestReceiptResponse latestReceiptResponse = verificationService.getLatestReceiptResponse(request);
        BeanLocatorFactory.getBean(VERIFY_IAP_FRAUD_DETECTION_CHAIN, IHandler.class).handle(new IapVerificationWrapperRequest(latestReceiptResponse, request));
        final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(IapVerificationRequestWrapper.builder().clientId(clientId).verificationRequest(request).receiptResponse(latestReceiptResponse).build());
        final Transaction transaction = transactionManager.init(transactionInitRequest);
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().extTxnId(latestReceiptResponse.getExtTxnId()).paymentCode(transaction.getPaymentChannel()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        final TransactionStatus initialStatus = transaction.getStatus();
        SessionContextHolder.<SessionDTO>getBody().put(PaymentConstants.TXN_ID, transaction.getId());
        try {
            return verificationService.verifyReceipt(latestReceiptResponse);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(initialStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(initialStatus, finalStatus, transaction);
        }
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest request) {
        final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(PlanRenewalRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).clientAlias(request.getClientAlias()).build());
        final Transaction transaction = transactionManager.init(transactionInitRequest);
        final TransactionStatus initialStatus = transaction.getStatus();
        final IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> merchantPaymentRenewalService = BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), new ParameterizedTypeReference<IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>>() {
        });
        try {
            return merchantPaymentRenewalService.doRenewal(request);
        } finally {
            if (merchantPaymentRenewalService.supportsRenewalReconciliation()) {
                sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
            }
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(initialStatus).finalTransactionStatus(finalStatus).attemptSequence(request.getAttemptSequence() + 1).transactionId(request.getId()).build());
        }
    }

    public void addToPaymentRenewalMigration(MigrationTransactionRequest request) {
        final Calendar nextChargingDate = Calendar.getInstance();
        final Map<String, String> paymentMetaData = request.getPaymentMetaData();
        nextChargingDate.setTime(request.getNextChargingDate());
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request));
        paymentMetaData.put(MIGRATED, Boolean.TRUE.toString());
        paymentMetaData.put(TXN_ID, transaction.getIdStr());
        final IMerchantTransactionDetailsService merchantTransactionDetailsService = BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), IMerchantTransactionDetailsService.class);
        final MerchantTransaction merchantTransaction = merchantTransactionDetailsService.getMerchantTransactionDetails(paymentMetaData);
        merchantTransactionService.upsert(merchantTransaction);
        transactionManager.revision(MigrationTransactionRevisionRequest.builder().nextChargingDate(nextChargingDate).transaction(transaction).existingTransactionStatus(TransactionStatus.INPROGRESS).finalTransactionStatus(transaction.getStatus()).build());
    }

    public WynkResponseEntity<WalletTopUpResponse> topUp(WalletTopUpRequest<?> request) {
        BeanLocatorFactory.getBean(CHARGING_FRAUD_DETECTION_CHAIN, IHandler.class).handle(request);
        final PaymentCode paymentCode = paymentMethodCache.get(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request.getPurchaseDetails()));
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        return BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IWalletTopUpService<WalletTopUpResponse, WalletTopUpRequest<?>>>() {
        }).topUp(request);
    }

    private WynkResponseEntity<AbstractChargingStatusResponse> internalStatus(AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final PaymentCode paymentCode = transaction.getPaymentChannel();
        final IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest>>() {
        });
        request.setPlanId(transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(transaction.getPlanId()).getLinkedFreePlanId() : transaction.getPlanId());
        try {
            return statusService.status(request);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw e;
        }
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

    private void publishEventsOnReconcileCompletion(TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

}