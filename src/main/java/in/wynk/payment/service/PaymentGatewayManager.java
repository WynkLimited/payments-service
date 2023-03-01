package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
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
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.*;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.apb.ApbConstants;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
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
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;

@Slf4j
@Service(BeanConstant.PAYMENT_MANAGER_V2)
@RequiredArgsConstructor
public class PaymentGatewayManager
        implements IMerchantPaymentRenewalServiceV2<PaymentRenewalChargingMessage>, IPaymentCallback<CallbackResponseWrapper<? extends AbstractPaymentCallbackResponse>, CallbackRequestWrapper<?>>,
        IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IVerificationService<AbstractVerificationResponse, VerificationRequestV2>, IPreDebitNotificationService {

    private final ICouponManager couponManager;
    //ßprivate final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ISqsManagerService<Object> sqsManagerService;
    private final ITransactionManagerService transactionManager;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IMerchantTransactionService merchantTransactionService;
    //private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;
    private final Map<Class<? extends AbstractTransactionStatusRequest>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>> statusDelegator = new HashMap<>();
    private final Gson gson;

    @PostConstruct
    public void init () {
        this.statusDelegator.put(ChargingTransactionStatusRequest.class, new ChargingTransactionStatusService());
        this.statusDelegator.put(AbstractTransactionReconciliationStatusRequest.class, new ReconciliationTransactionStatusService());
    }

    @FraudAware(name = CHARGING_FRAUD_DETECTION_CHAIN)
    public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
        PaymentGateway paymentGateway = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request), request);
        final IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> chargingService =
                BeanLocatorFactory.getBean(paymentGateway.getCode().concat(CHARGE),
                        new ParameterizedTypeReference<IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>>() {
                        });

        try {
            AbstractCoreChargingResponse response = chargingService.charge(request);
            if (paymentGateway.isPreDebit()) {
                final TransactionStatus finalStatus = TransactionContext.get().getStatus();
                final TransactionStatus existingStatus = transaction.getStatus();
                transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
                exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            }
            return response;
        } catch (Exception ex) {
            this.handleGatewayFailure(ex);
            throw ex;
        } finally {
            eventPublisher.publishEvent(PurchaseInitEvent.builder().clientAlias(transaction.getClientAlias()).transactionId(transaction.getIdStr()).uid(transaction.getUid()).msisdn(transaction
                    .getMsisdn()).productDetails(request.getProductDetails()).appDetails(request.getAppDetails()).sid(
                    Optional.ofNullable(SessionContextHolder.getId())).build());
            sqsManagerService.publishSQSMessage(
                    PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel().getId()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                            .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        }
    }

    @Override
    public AbstractVerificationResponse verify (VerificationRequestV2 request) {
        String paymentCode;
        Client client = ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT002));
        Optional<Boolean> verifyPGOptional = client.getMeta(BaseConstants.DEFAULT_VERIFY_PAYMENT_GATEWAY);
        if (verifyPGOptional.isPresent() && verifyPGOptional.get()) {
            Optional<String> verifyPaymentCode = client.getMeta(BaseConstants.DEFAULT_VERIFY_PAYMENT_GATEWAY);
            paymentCode = verifyPaymentCode.get();
        } else {
            log.warn(PaymentLoggingMarker.PAYMENT_VERIFY_USER_PAYMENT_FAILURE, "default verify gateway is not registered in client : {}", client.getAlias());
            paymentCode = BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE + VERSION_2;
        }
        AnalyticService.update(PAYMENT_METHOD, paymentCode.toUpperCase());
        final IVerificationService<AbstractVerificationResponse, VerificationRequestV2> verifyService =
                BeanLocatorFactory.getBean(paymentCode, new ParameterizedTypeReference<IVerificationService<AbstractVerificationResponse, VerificationRequestV2>>() {
                });
        return verifyService.verify(request);
    }


    private void exhaustCouponIfApplicable (TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
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

    private void handleGatewayFailure (Exception ex) {
        final Transaction transaction = TransactionContext.get();
        final PaymentErrorEvent.Builder eventBuilder = PaymentErrorEvent.builder(transaction.getIdStr()).clientAlias(transaction.getClientAlias());
        if (ex instanceof WynkRuntimeException) {
            final WynkRuntimeException original = (WynkRuntimeException) ex;
            final IWynkErrorType errorType = original.getErrorType();
            eventBuilder.code(errorType.getErrorCode());
            eventBuilder.code(errorType.getErrorMessage());
        } else {
            eventBuilder.code(PaymentErrorType.PAY002.getErrorCode()).description(ex.getMessage());
        }
        eventPublisher.publishEvent(eventBuilder.build());
    }

    private void publishEventsOnReconcileCompletion (TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(in.wynk.common.enums.PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

    private void reviseTransactionAndExhaustCoupon (Transaction transaction, TransactionStatus existingStatus,
                                                    AbstractTransactionRevisionRequest abstractTransactionRevisionRequest) {
        transactionManager.revision(abstractTransactionRevisionRequest);
        exhaustCouponIfApplicable(existingStatus, transaction.getStatus(), transaction);
    }

    @Override
    public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
        final IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> delegatorStatusService =
                statusDelegator.get(request.getClass());
        if (Objects.isNull(delegatorStatusService)) {
            throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return delegatorStatusService.status(request);
    }

    @Override
    @TransactionAware(txnId = "#request.transactionId")
    public CallbackResponseWrapper<AbstractPaymentCallbackResponse> handleCallback (CallbackRequestWrapper<?> request) {
        final PaymentGateway pg = request.getPaymentGateway();
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest> callbackService =
                BeanLocatorFactory.getBean(pg.getCode().concat(CALLBACK), new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest>>() {
                });
        try {
            final AbstractPaymentCallbackResponse response = callbackService.handleCallback(request.getBody());
            if (pg.isPreDebit() && Objects.nonNull(response)) {
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(PaymentErrorType.PAY302.getErrorCode()).description(PaymentErrorType.PAY302.getErrorMessage()).build());
            }
            return CallbackResponseWrapper.builder().callbackResponse(response).transaction(transaction).build();
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            final TransactionStatus finalStatus = TransactionContext.get().getStatus();
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            //publishBranchEvent(PaymentsBranchEvent.<EventsWrapper>builder().eventName(PAYMENT_CALLBACK_EVENT).data(getEventsWrapperBuilder(transaction, TransactionContext.getPurchaseDetails())
            // .callbackRequest(request.getBody()).build()).build());
        }
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
                final in.wynk.common.enums.PaymentEvent event = receiptDetailService.getPaymentEvent(wrapper);
                final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(
                        PlanRenewalRequest.builder().planId(mapping.getPlanId()).uid(mapping.getUid()).msisdn(mapping.getMsisdn()).paymentGateway(request.getPaymentGateway())
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
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
        }
    }

    @Override
    public void renew (PaymentRenewalChargingMessage request) {
        PaymentGateway paymentGateway = PaymentCodeCachingService.getFromPaymentCode(request.getPaymentCode());
        final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(
                PlanRenewalRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentGateway(paymentGateway)
                        .clientAlias(request.getClientAlias()).build());
        final Transaction transaction = transactionManager.init(transactionInitRequest);
        final TransactionStatus initialStatus = transaction.getStatus();
        final IMerchantPaymentRenewalServiceV2<PaymentRenewalChargingMessage> renewalService =
                BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode().concat(VERSION_2),
                        new ParameterizedTypeReference<IMerchantPaymentRenewalServiceV2<PaymentRenewalChargingMessage>>() {
                        });
        final MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            renewalService.renew(request);
        } catch(RestClientException e) {
            PaymentErrorEvent.Builder errorEventBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
            if (e.getRootCause() != null) {
                if (e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException) {
                    log.error(RENEWAL_STATUS_ERROR, "Socket timeout but valid for reconciliation for request : due to {}", e.getMessage(), e);
                    errorEventBuilder.code(PAY036.getErrorCode());
                    errorEventBuilder.description(PAY036.getErrorMessage() + "for "+ paymentGateway);
                    eventPublisher.publishEvent(errorEventBuilder.build());
                    throw new WynkRuntimeException(PAY036);
                } else {
                    handleException(errorEventBuilder, paymentGateway, e);
                }
            } else {
                handleException(errorEventBuilder,paymentGateway, e);
            }
        } finally{
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
            if (renewalService.supportsRenewalReconciliation()) {
                sqsManagerService.publishSQSMessage(
                        PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel().getId()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                                .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid())
                                .originalAttemptSequence(request.getAttemptSequence() + 1).originalTransactionId(request.getId()).build());
            }
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(initialStatus).finalTransactionStatus(finalStatus)
                    .attemptSequence(request.getAttemptSequence() + 1).transactionId(request.getId()).build());
        }
    }

    private void handleException (PaymentErrorEvent.Builder errorEventBuilder, PaymentGateway paymentGateway, RestClientException e) {
        errorEventBuilder.code(PAY024.getErrorCode());
        errorEventBuilder.description(PAY024.getErrorMessage()+"for "+paymentGateway);
        eventPublisher.publishEvent(errorEventBuilder.build());
        throw new WynkRuntimeException(PAY024, e);
    }

    @Override
    public boolean supportsRenewalReconciliation () {
        return IMerchantPaymentRenewalServiceV2.super.supportsRenewalReconciliation();
    }

    @Override
    public AbstractPreDebitNotificationResponse notify (PreDebitNotificationMessage message) {
        log.info(PaymentLoggingMarker.PRE_DEBIT_NOTIFICATION_QUEUE, "processing PreDebitNotificationMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        AbstractPreDebitNotificationResponse preDebitResponse = BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), IPreDebitNotificationService.class).notify(message);
        AnalyticService.update(ApbConstants.PRE_DEBIT_SI, gson.toJson(preDebitResponse));
        return preDebitResponse;
    }

    private class ChargingTransactionStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        @TransactionAware(txnId = "#request.transactionId", lock = false)
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            final TransactionStatus txnStatus = transaction.getStatus();
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(txnStatus).transactionType(transaction.getType()).build();
        }
    }

    private class ReconciliationTransactionStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        @TransactionAware(txnId = "#request.transactionId")
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            final TransactionStatus existingStatus = transaction.getStatus();
            try {
                final PaymentGateway paymentGateway = transaction.getPaymentChannel();
                final IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> statusService =
                        BeanLocatorFactory.getBean(paymentGateway.getCode() + VERSION_2,
                                new ParameterizedTypeReference<IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>() {
                                });
                return statusService.status(request);
            } finally {
                final TransactionStatus finalStatus = transaction.getStatus();
                AnalyticService.update(PAYMENT_METHOD, transaction.getPaymentChannel().name());
                AsyncTransactionRevisionRequest.AsyncTransactionRevisionRequestBuilder builder =
                        AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus);
                if (transaction.getType() == PaymentEvent.RENEW) {
                    RenewalChargingTransactionReconciliationStatusRequest renewalChargingTransactionReconciliationStatusRequest = (RenewalChargingTransactionReconciliationStatusRequest) request;
                    builder.attemptSequence(renewalChargingTransactionReconciliationStatusRequest.getOriginalAttemptSequence())
                            .transactionId(renewalChargingTransactionReconciliationStatusRequest.getOriginalTransactionId());
                }
                reviseTransactionAndExhaustCoupon(transaction, existingStatus, builder.build());
                publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
            }
        }
    }
}
