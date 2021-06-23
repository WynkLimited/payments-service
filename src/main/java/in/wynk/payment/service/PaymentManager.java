package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.AbstractErrorDetails;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.common.messages.PaymentRecurringSchedulingMessage;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.StatusMode;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.ClientCallbackEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.ClientCallbackPayloadWrapper;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
import in.wynk.payment.mapper.S2STransactionInitRequestMapper;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.MIGRATED;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.TXN_ID;

@Slf4j
@Service
public class PaymentManager implements IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>, IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequestWrapper>, IMerchantPaymentRefundService<AbstractPaymentRefundResponse, PaymentRefundInitRequest>, IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ISqsManagerService sqsManagerService;
    private final ApplicationEventPublisher eventPublisher;
    private final IClientCallbackService clientCallbackService;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;

    public PaymentManager(ICouponManager couponManager, PaymentCachingService cachingService, ISqsManagerService sqsManagerService, ApplicationEventPublisher eventPublisher, IClientCallbackService clientCallbackService, ITransactionManagerService transactionManager, IMerchantTransactionService merchantTransactionService) {
        this.couponManager = couponManager;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
        this.clientCallbackService = clientCallbackService;
        this.merchantTransactionService = merchantTransactionService;
    }

    @TransactionAware(txnId = "#request.originalTransactionId")
    public WynkResponseEntity<AbstractPaymentRefundResponse> refund(PaymentRefundInitRequest request) {
        final Transaction originalTransaction = TransactionContext.get();
        final AbstractTransactionInitRequest refundTransactionInitRequest = DefaultTransactionInitRequestMapper.from(RefundTransactionRequestWrapper.builder().request(request).originalTransaction(originalTransaction).build());
        try {
            final Transaction refundTransaction = transactionManager.init(refundTransactionInitRequest);
            final String externalReferenceId = merchantTransactionService.getPartnerReferenceId(request.getOriginalTransactionId());
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
    public WynkResponseEntity<AbstractChargingResponse> doCharging(AbstractChargingRequest<?> request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final AbstractTransactionInitRequest refundTransactionInitRequest = DefaultTransactionInitRequestMapper.from(request);
        final Transaction transaction = transactionManager.init(refundTransactionInitRequest);
        final TransactionStatus existingStatus = transaction.getStatus();
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        final IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>> chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>>() {
        });
        final WynkResponseEntity<AbstractChargingResponse> response = chargingService.doCharging(request);
        if (paymentCode.isPreDebit()) {
            WynkResponseEntity.WynkBaseResponse<?, ?> body = response.getBody();
            if (Objects.nonNull(body) && !body.isSuccess()) {
                AbstractErrorDetails errorDetails = (AbstractErrorDetails) body.getError();
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorDetails.getCode()).description(errorDetails.getDescription()).build());
            }
            TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
        }
        return response;
    }

    @TransactionAware(txnId = "#request.transactionId")
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(CallbackRequestWrapper request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest> callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest>>() {
        });
        try {
            final WynkResponseEntity<AbstractCallbackResponse> baseResponse = callbackService.handleCallback(request);
            if (paymentCode.isPreDebit()) {
                WynkResponseEntity.WynkBaseResponse<?, ?> wynkBaseResponse = baseResponse.getBody();
                if (Objects.nonNull(wynkBaseResponse) && !wynkBaseResponse.isSuccess()) {
                    AbstractErrorDetails errorDetails = (AbstractErrorDetails) wynkBaseResponse.getError();
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorDetails.getCode()).description(errorDetails.getDescription()).build());
                }
            }
            return baseResponse;
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
        }

    }

    @ClientAware(clientAlias = "#clientAlias")
    public WynkResponseEntity<?> handleNotification(String clientAlias, CallbackRequest callbackRequest, PaymentCode paymentCode) {
        final IReceiptDetailService receiptDetailService = BeanLocatorFactory.getBean(paymentCode.getCode(), IReceiptDetailService.class);
        final Optional<ReceiptDetails> optionalReceiptDetails = receiptDetailService.getReceiptDetails(callbackRequest);
        if (optionalReceiptDetails.isPresent()) {
            final ReceiptDetails receiptDetails = optionalReceiptDetails.get();
            final AbstractTransactionInitRequest transactionInitRequest = S2STransactionInitRequestMapper.from(PlanRenewalRequest.builder().planId(receiptDetails.getPlanId()).uid(receiptDetails.getUid()).msisdn(receiptDetails.getMsisdn()).paymentCode(paymentCode).clientAlias(clientAlias).build());
            final Transaction transaction = transactionManager.init(transactionInitRequest);
            return handleCallback(CallbackRequestWrapper.builder().paymentCode(paymentCode).body(callbackRequest.getBody()).transactionId(transaction.getItemId()).build());
        }
        return WynkResponseEntity.builder().success(false).build();
    }

    @TransactionAware(txnId = "#request.transactionId")
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final PaymentCode paymentCode = transaction.getPaymentChannel();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> statusService = BeanLocatorFactory.getBean(paymentCode.getCode(), new ParameterizedTypeReference<IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest>>() {
        });
        request.setPlanId(transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(transaction.getPlanId()).getLinkedFreePlanId() : transaction.getPlanId());
        try {
            AnalyticService.update(PAYMENT_METHOD, paymentCode.name());
            return statusService.status(request);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw e;
        } finally {
            if (request.getMode() == StatusMode.SOURCE) {
                TransactionStatus finalStatus = transaction.getStatus();
                transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
                if (existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
                    exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
                }
                publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
            }
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
        final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(DefaultChargingRequest.builder().paymentMode(paymentCode.getCode()).merchantName(paymentCode.getCode()).paymentCode(paymentCode).chargingDetails(AbstractChargingRequest.PlanS2SChargingDetails.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).autoRenew(latestReceiptResponse.isAutoRenewal()).trialOpted(latestReceiptResponse.isFreeTrial()).build()).build());
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

    public void doRenewal(PaymentRenewalChargingRequest request, PaymentCode paymentCode) {
        final AbstractTransactionInitRequest transactionInitRequest = S2STransactionInitRequestMapper.from(PlanRenewalRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(paymentCode).clientAlias(request.getClientAlias()).build());
        final Transaction transaction = transactionManager.init(transactionInitRequest);
        final TransactionStatus initialStatus = transaction.getStatus();
        final IMerchantPaymentRenewalService merchantPaymentRenewalService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentRenewalService.class);
        try {
            merchantPaymentRenewalService.doRenewal(request);
        } finally {
            if (merchantPaymentRenewalService.supportsRenewalReconciliation()) {
                sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
            }
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(initialStatus).finalTransactionStatus(finalStatus).attemptSequence(request.getAttemptSequence() + 1).build());
        }
    }

    public void sendClientCallback(String clientAlias, ClientCallbackRequest request) {
        clientCallbackService.sendCallback(ClientCallbackPayloadWrapper.<ClientCallbackRequest>builder().clientAlias(clientAlias).payload(request).build());
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

    public void addToPaymentRenewalMigration(PaymentRecurringSchedulingMessage message) {
        Calendar nextChargingDate = Calendar.getInstance();
        nextChargingDate.setTime(message.getNextChargingDate());
        int planId = message.getPlanId();
        PaymentCode paymentCode = PaymentCode.getFromCode(message.getPaymentCode());
        double amount = cachingService.getPlan(planId).getFinalPrice();
        // TODO:: revision is required
        Transaction transaction = transactionManager.init(PlanTransactionInitRequest.builder()
                .uid(message.getUid())
                .msisdn(message.getMsisdn())
                .clientAlias(message.getClientAlias())
                .planId(planId)
                .amount(amount)
                .paymentCode(paymentCode)
                .event(message.getEvent())
                .autoRenewOpted(Boolean.TRUE)
                .status(TransactionStatus.MIGRATED.getValue())
                .build());
        IMerchantTransactionDetailsService merchantTransactionDetailsService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantTransactionDetailsService.class);
        message.getPaymentMetaData().put(MIGRATED, Boolean.TRUE.toString());
        message.getPaymentMetaData().put(TXN_ID, transaction.getIdStr());
        MerchantTransaction merchantTransaction = merchantTransactionDetailsService.getMerchantTransactionDetails(message.getPaymentMetaData());
        merchantTransactionService.upsert(merchantTransaction);
        transactionManager.revision(MigrationTransactionRevisionRequest.builder().nextChargingDate(nextChargingDate).transaction(transaction).existingTransactionStatus(TransactionStatus.INPROGRESS).finalTransactionStatus(transaction.getStatus()).build());
    }

    private void publishEventsOnReconcileCompletion(TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

    public BaseResponse addMoney(String uid, String msisdn, WalletAddMoneyRequest request) {
        final PaymentCode paymentCode = request.getPaymentCode();
        final Transaction transaction = null;//initiateTransaction(false, request.getPlanId(), uid, msisdn, request.getItemId(), null, paymentCode);
        sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel()).transactionId(transaction.getIdStr()).paymentEvent(transaction.getType()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        return BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantWalletService.class).addMoney(request);
    }

}