package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.constant.BaseConstants;
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
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.core.event.*;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractAcknowledgement;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlayProductAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlaySubscriptionAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.stream.producer.IKafkaPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.MIGRATED;
import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.BeanConstant.VERIFY_IAP_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service(BeanConstant.PAYMENT_MANAGER)
@RequiredArgsConstructor
public class PaymentManager
        implements IMerchantIapSubscriptionAcknowledgementService, IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>,
        IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequestWrapper<?>>,
        IMerchantPaymentRefundService<AbstractPaymentRefundResponse, PaymentRefundInitRequest>,
        IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionReconciliationStatusRequest>, IWalletTopUpService<WalletTopUpResponse, WalletTopUpRequest<?>>,
        IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, IMerchantPaymentSettlement<AbstractPaymentSettlementResponse, PaymentSettlementRequest>, IMerchantTDRService {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ISqsManagerService<Object> sqsManagerService;
    private final IKafkaPublisherService<String, Object> kafkaPublisherService;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;
    private final PaymentGatewayCommon common;

    private static final List<Integer> NOTIFICATION = Arrays.asList(4, 6, 11, 13);
    public static final Integer PURCHASE_NOTIFICATION_TYPE = 4;

    @TransactionAware(txnId = "#request.originalTransactionId")
    public WynkResponseEntity<AbstractPaymentRefundResponse> refund (PaymentRefundInitRequest request) {
        final Transaction originalTransaction = TransactionContext.get();
        try {
            final String externalReferenceId = getExternalReferenceId(request.getOriginalTransactionId());
            final Transaction refundTransaction =
                    transactionManager.init(DefaultTransactionInitRequestMapper.from(
                            RefundTransactionRequestWrapper.builder().request(request).txnId(originalTransaction.getIdStr()).originalTransaction(originalTransaction).build()));
            final IMerchantPaymentRefundService<AbstractPaymentRefundResponse, AbstractPaymentRefundRequest> refundService = BeanLocatorFactory.getBean(refundTransaction.getPaymentChannel().getCode(),
                    new ParameterizedTypeReference<IMerchantPaymentRefundService<AbstractPaymentRefundResponse, AbstractPaymentRefundRequest>>() {
                    });
            final AbstractPaymentRefundRequest refundRequest = AbstractPaymentRefundRequest.from(originalTransaction, externalReferenceId, request.getReason());
            final WynkResponseEntity<AbstractPaymentRefundResponse> refundInitResponse = refundService.refund(refundRequest);
            if (Objects.nonNull(refundInitResponse.getBody())) {
                final AbstractPaymentRefundResponse refundResponse = refundInitResponse.getBody().getData();
                if (refundResponse.getTransactionStatus() != TransactionStatus.FAILURE) {
                    kafkaPublisherService.publishKafkaMessage(
                            PaymentReconciliationMessage.builder().paymentMethodId(common.getPaymentId(transactionManager.get(request.getOriginalTransactionId())))
                                    .paymentCode(refundTransaction.getPaymentChannel().getId()).extTxnId(refundResponse.getExternalReferenceId())
                                    .transactionId(refundTransaction.getIdStr()).paymentEvent(refundTransaction.getType()).itemId(refundTransaction.getItemId()).planId(refundTransaction.getPlanId())
                                    .msisdn(refundTransaction.getMsisdn()).uid(refundTransaction.getUid()).build());
                }
            }
            return refundInitResponse;
        } catch (WynkRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY020, e);
        }
    }

    private String getExternalReferenceId (String originalTransactionId) {
        try {
            return merchantTransactionService.getPartnerReferenceId(originalTransactionId);
        } catch (Exception e) {
            log.error("Exception Occurred while getting merchant transaction reference id while refunding the amount");
            return null;
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingResponse> charge (AbstractChargingRequest<?> request) {
        BeanLocatorFactory.getBean(CHARGING_FRAUD_DETECTION_CHAIN, IHandler.class).handle(request);
        final PaymentGateway paymentGateway = paymentMethodCache.get(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request.getPurchaseDetails()), request.getPurchaseDetails());
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>> chargingService =
                BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>>() {
                });
        try {
            final WynkResponseEntity<AbstractChargingResponse> response = chargingService.charge(request);
            if (paymentGateway.isPreDebit()) {
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
            if (BaseConstants.PLAN.equals(request.getProductDetails().getType())) {
                eventPublisher.publishEvent(PurchaseInitEvent.builder().clientAlias(transaction.getClientAlias()).transactionId(transaction.getIdStr()).uid(transaction.getUid()).msisdn(transaction
                                .getMsisdn()).productDetails(request.getPurchaseDetails().getProductDetails()).appDetails(request.getPurchaseDetails().getAppDetails())
                        .sid(Optional.ofNullable(SessionContextHolder
                                .getId())).build());
            }
            kafkaPublisherService.publishKafkaMessage(
                    PaymentReconciliationMessage.builder().paymentMethodId(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).paymentCode(transaction.getPaymentChannel().getId())
                            .paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                            .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
            //publishBranchEvent(PaymentsBranchEvent.<EventsWrapper>builder().eventName(PAYMENT_CHARGING_EVENT).data(getEventsWrapperBuilder(transaction, Optional.ofNullable(request
            // .getPurchaseDetails())).build()).build());
        }
    }

    @Override
    @TransactionAware(txnId = "#request.transactionId")
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback (CallbackRequestWrapper<?> request) {
        final PaymentGateway paymentGateway = request.getPaymentGateway();
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        final IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest> callbackService =
                BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest>>() {
                });
        try {
            final WynkResponseEntity<AbstractCallbackResponse> response = callbackService.handleCallback(request.getBody());
            if (paymentGateway.isPreDebit()) {
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
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            PaymentRenewal renewal= recurringPaymentManagerService.getRenewalById(transaction.getIdStr());
            int attemptSequence = Optional.ofNullable(renewal)
                    .map(ren -> ren.getAttemptSequence())
                    .orElse(0);
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).lastSuccessTransactionId(lastSuccessTransactionId).existingTransactionStatus(existingStatus)
                    .finalTransactionStatus(finalStatus).attemptSequence(attemptSequence).build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            //publishBranchEvent(PaymentsBranchEvent.<EventsWrapper>builder().eventName(PAYMENT_CALLBACK_EVENT).data(getEventsWrapperBuilder(transaction, TransactionContext.getPurchaseDetails())
            // .callbackRequest(request.getBody()).build()).build());
        }
    }

    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<Void> handleNotification (NotificationRequest request) {
        final IReceiptDetailService<?, IAPNotification> receiptDetailService =
                BeanLocatorFactory.getBean(request.getPaymentGateway().getCode(), new ParameterizedTypeReference<IReceiptDetailService<?, IAPNotification>>() {
                });
        DecodedNotificationWrapper<IAPNotification> wrapper = receiptDetailService.isNotificationEligible(request.getPayload());
        AnalyticService.update(wrapper.getDecodedNotification());
        if (wrapper.isEligible()) {
            final UserPlanMapping<?> mapping = receiptDetailService.getUserPlanMapping(wrapper);
            if (mapping != null) {
                final in.wynk.common.enums.PaymentEvent event = receiptDetailService.getPaymentEvent(wrapper, BaseConstants.PLAN);
                final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(
                        PlanRenewalRequest.builder().planId(mapping.getPlanId()).txnId(mapping.getLinkedTransactionId()).uid(mapping.getUid()).msisdn(mapping.getMsisdn())
                                .paymentGateway(request.getPaymentGateway())
                                .clientAlias(request.getClientAlias()).build());
                transactionInitRequest.setEvent(event);
                final Transaction transaction = transactionManager.init(transactionInitRequest);
                handleNotification(transaction, mapping);
                return WynkResponseEntity.<Void>builder().success(true).build();
            }
        }
        return WynkResponseEntity.<Void>builder().success(false).build();
    }

    private <T> void handleNotification (Transaction transaction, UserPlanMapping<T> mapping) {
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
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).lastSuccessTransactionId(lastSuccessTransactionId).existingTransactionStatus(existingStatus)
                    .finalTransactionStatus(finalStatus).build());
            if (finalStatus == TransactionStatus.SUCCESS) {
                //unsubscribe existing transaction plan id/transaction id
                ItunesReceiptDetails receiptDetails = (ItunesReceiptDetails) mapping.getMessage();
                Transaction  receiptTransaction = transactionManager.get(receiptDetails.getPaymentTransactionId());
                eventPublisher.publishEvent(RecurringPaymentEvent.builder().transaction(receiptTransaction).paymentEvent(PaymentEvent.UNSUBSCRIBE).build());
            }
        }
    }

    @TransactionAware(txnId = "#transactionId", lock = false)
    public WynkResponseEntity<AbstractChargingStatusResponse> status (String transactionId) {
        final Transaction transaction = TransactionContext.get();
        if (Objects.isNull(transaction.getPlanId())) {
            if (Objects.isNull(transaction.getItemId())) {
                throw new WynkRuntimeException(PaymentErrorType.PAY025);
            }
            return internalStatus(ChargingTransactionStatusRequest.builder().transactionId(transactionId).itemId(transaction.getItemId()).build());
        }
        return internalStatus(ChargingTransactionStatusRequest.builder().transactionId(transactionId).planId(transaction.getPlanId()).build());
    }

    @TransactionAware(txnId = "#transactionId", lock = false)
    public TransactionSnapShot statusV2 (String transactionId) {
        final Transaction transaction = TransactionContext.get();
        final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElse(null);
        return TransactionSnapShot.builder().transactionDetails(TransactionDetails.builder().transaction(transaction).purchaseDetails(purchaseDetails).build()).build();
    }

    @Override
    @TransactionAware(txnId = "#request.transactionId")
    public WynkResponseEntity<AbstractChargingStatusResponse> status (AbstractTransactionReconciliationStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final TransactionStatus existingStatus = transaction.getStatus();
        try {
            return internalStatus(request);
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            AnalyticService.update(PAYMENT_METHOD, transaction.getPaymentChannel().name());
            AsyncTransactionRevisionRequest.AsyncTransactionRevisionRequestBuilder builder =
                    AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus);
            if (transaction.getType() == PaymentEvent.RENEW) {
                RenewalChargingTransactionReconciliationStatusRequest renewalChargingTransactionReconciliationStatusRequest = (RenewalChargingTransactionReconciliationStatusRequest) request;
                builder.attemptSequence(renewalChargingTransactionReconciliationStatusRequest.getOriginalAttemptSequence())
                        .originalTransactionId(renewalChargingTransactionReconciliationStatusRequest.getOriginalTransactionId());
            }
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            builder.lastSuccessTransactionId(lastSuccessTransactionId);
            transactionManager.revision(builder.build());
            exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
            //publishBranchEvent(PaymentsBranchEvent.<EventsWrapper>builder().eventName(PAYMENT_RECONCILE_EVENT).data(getEventsWrapperBuilder(transaction, TransactionContext.getPurchaseDetails())
            // .extTxnId(request.getExtTxnId()).build()).build());
        }
    }

    private EventsWrapper.EventsWrapperBuilder getEventsWrapperBuilder (Transaction transaction, Optional<IPurchaseDetails> purchaseDetails) {
        return EventsWrapper.builder()
                .transaction(transaction)
                .uid(transaction.getUid())
                .item(transaction.getItemId())
                .msisdn(transaction.getMsisdn())
                .planId(transaction.getPlanId())
                .amount(transaction.getAmount())
                .coupon(transaction.getCoupon())
                .discount(transaction.getDiscount())
                .transactionId(transaction.getIdStr())
                .clientAlias(transaction.getClientAlias())
                .paymentEvent(transaction.getType().name())
                .triggerDate(EventsWrapper.getTriggerDate())
                .transactionStatus(transaction.getStatus().name())
                .paymentCode(transaction.getPaymentChannel().name())
                .appDetails(purchaseDetails.map(IPurchaseDetails::getAppDetails).orElse(null))
                .userDetails(purchaseDetails.map(IPurchaseDetails::getUserDetails).orElse(null))
                .productDetails(purchaseDetails.map(IPurchaseDetails::getProductDetails).orElse(null))
                .paymentDetails(purchaseDetails.map(IPurchaseDetails::getPaymentDetails).orElse(null))
                .os(purchaseDetails.map(IPurchaseDetails::getAppDetails).map(IAppDetails::getOs).orElse(null))
                .deviceId(purchaseDetails.map(IPurchaseDetails::getAppDetails).map(IAppDetails::getDeviceId).orElse(null))
                .isTrialOpted(purchaseDetails.map(IPurchaseDetails::getPaymentDetails).map(IPaymentDetails::isTrialOpted).orElse(null))
                .paymentMode(purchaseDetails.map(IPurchaseDetails::getPaymentDetails).map(IPaymentDetails::getPaymentMode).orElse(null))
                .optForAutoRenew(purchaseDetails.map(IPurchaseDetails::getPaymentDetails).map(IPaymentDetails::isAutoRenew).orElse(null));
    }

    @ClientAware(clientId = "#clientId")
    public BaseResponse<?> doVerifyIap (String clientId, IapVerificationRequest request) {
        final PaymentGateway paymentGateway = request.getPaymentGateway();
        final IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentGateway.getCode(), IMerchantIapPaymentVerificationService.class);
        final LatestReceiptResponse latestReceiptResponse = verificationService.getLatestReceiptResponse(request);
        try {
            BeanLocatorFactory.getBean(VERIFY_IAP_FRAUD_DETECTION_CHAIN, IHandler.class).handle(new IapVerificationWrapperRequest(latestReceiptResponse, request, null));
        } catch (WynkRuntimeException e) {
            if (e.getErrorCode().equalsIgnoreCase(PaymentErrorType.PAY701.getErrorCode())) {
                //means receipt is already processed, no need for subscription provision again
                final SessionDTO sessionDTO = SessionContextHolder.getBody();
                TransactionContext.set(TransactionDetails.builder().transaction(transactionManager.get(sessionDTO.get(TXN_ID))).purchaseDetails(request.getPurchaseDetails()).build());
                return verificationService.verifyReceipt(latestReceiptResponse);
            }
            throw e;
        }
        final AbstractTransactionInitRequest transactionInitRequest =
                DefaultTransactionInitRequestMapper.from(IapVerificationRequestWrapper.builder().clientId(clientId).verificationRequest(request).receiptResponse(latestReceiptResponse).build());
        final Transaction transaction = transactionManager.init(transactionInitRequest, request.getPurchaseDetails());
        kafkaPublisherService.publishKafkaMessage(
                PaymentReconciliationMessage.builder().extTxnId(latestReceiptResponse.getExtTxnId()).paymentCode(transaction.getPaymentChannel().getId()).transactionId(transaction.getIdStr())
                        .paymentEvent(transaction.getType()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        final TransactionStatus initialStatus = transaction.getStatus();
        SessionContextHolder.<SessionDTO>getBody().put(TXN_ID, transaction.getId());
        try {
            return verificationService.verifyReceipt(latestReceiptResponse);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).lastSuccessTransactionId(lastSuccessTransactionId).existingTransactionStatus(initialStatus)
                    .finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(initialStatus, finalStatus, transaction);
        }
    }

    @ClientAware(clientId = "#clientId")
    public BaseResponse<?> doVerifyIap (String clientId, IapVerificationRequestV2Wrapper requestWrapper) {
        IapVerificationRequestV2 request = requestWrapper.getIapVerificationV2();
        String paymentCode = request.getPaymentCode().getCode();
        LatestReceiptResponse latestReceiptResponse = requestWrapper.getLatestReceiptResponse();
        final IMerchantIapPaymentVerificationService verificationService = BeanLocatorFactory.getBean(paymentCode, IMerchantIapPaymentVerificationService.class);
        //if null means call is coming from controller
        if (Objects.isNull(latestReceiptResponse)) {
            latestReceiptResponse = verificationService.getLatestReceiptResponse(request);
            IMerchantIapPaymentPreVerificationService preVerificationService = BeanLocatorFactory.getBean(paymentCode, IMerchantIapPaymentPreVerificationService.class);
            preVerificationService.verifyRequest(IapVerificationRequestV2Wrapper.builder().iapVerificationV2(request).latestReceiptResponse(latestReceiptResponse).build());
        }
        BeanLocatorFactory.getBean(VERIFY_IAP_FRAUD_DETECTION_CHAIN, IHandler.class).handle(new IapVerificationWrapperRequest(latestReceiptResponse, null, request));
        final AbstractTransactionInitRequest transactionInitRequest =
                DefaultTransactionInitRequestMapper.fromV2(IapVerificationRequestWrapper.builder().clientId(clientId).verificationRequestV2(request).receiptResponse(latestReceiptResponse).build(),
                        cachingService);
        final Transaction transaction = transactionManager.init(transactionInitRequest, request.getPurchaseDetails());
        kafkaPublisherService.publishKafkaMessage(
                PaymentReconciliationMessage.builder().extTxnId(latestReceiptResponse.getExtTxnId()).paymentCode(transaction.getPaymentChannel().getId()).transactionId(transaction.getIdStr())
                        .paymentEvent(transaction.getType()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        final TransactionStatus initialStatus = transaction.getStatus();
        SessionContextHolder.<SessionDTO>getBody().put(TXN_ID, transaction.getId());
        try {
            return verificationService.verifyReceipt(latestReceiptResponse);
        } catch (WynkRuntimeException e) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(String.valueOf(e.getErrorCode())).description(e.getErrorTitle()).build());
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
        } finally {
            final TransactionStatus finalStatus = transaction.getStatus();
            String lastSuccessTransactionId = getLastSuccessTransactionId(transaction);
            transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).lastSuccessTransactionId(lastSuccessTransactionId).existingTransactionStatus(initialStatus)
                    .finalTransactionStatus(finalStatus).build());
            exhaustCouponIfApplicable(initialStatus, finalStatus, transaction);
            if ((transaction.getStatus() == TransactionStatus.SUCCESS) && (request.getPaymentCode().getCode().equals(BeanConstant.GOOGLE_PLAY))) {
                AbstractAcknowledgement acknowledgementRequest = getRequest(request, latestReceiptResponse, transaction);
                if(Objects.nonNull(acknowledgementRequest)) {
                    publishAsync(acknowledgementRequest);
                }
            }
        }
    }

    private AbstractAcknowledgement getRequest (IapVerificationRequestV2 request, LatestReceiptResponse latestReceiptResponse, Transaction transaction) {
        String txnId = transaction.getIdStr();
        GooglePlayLatestReceiptResponse googleResponse = (GooglePlayLatestReceiptResponse) latestReceiptResponse;
        GooglePlayVerificationRequest googleRequest = (GooglePlayVerificationRequest) request;
        if (transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            return GooglePlaySubscriptionAcknowledgementRequest.builder()
                    .developerPayload(googleResponse.getGooglePlayResponse().getDeveloperPayload())
                    .productDetails(googleRequest.getProductDetails())
                    .appDetails(googleRequest.getAppDetails())
                    .paymentDetails(googleRequest.getPaymentDetails())
                    .paymentCode(request.getPaymentCode().getCode())
                    .txnId(txnId)
                    .build();
        } else if (transaction.getType() == PaymentEvent.POINT_PURCHASE) {
            return GooglePlayProductAcknowledgementRequest.builder()
                    .developerPayload(googleResponse.getGooglePlayResponse().getDeveloperPayload())
                    .productDetails(googleRequest.getProductDetails())
                    .appDetails(googleRequest.getAppDetails())
                    .paymentDetails(googleRequest.getPaymentDetails())
                    .paymentCode(request.getPaymentCode().getCode())
                    .txnId(txnId)
                    .build();
        }
       throw new WynkRuntimeException("Exception occurred as type is missing");
    }

    @Override
    public WynkResponseEntity<Void> doRenewal (PaymentRenewalChargingRequest request) {
        Optional<Transaction> originalTransaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(request.getId());
        if (request.getPaymentGateway().getId().equals(ITUNES) && originalTransaction.get().getStatus() != TransactionStatus.SUCCESS && !isEligibleForRenewal(originalTransaction.get())) {
            log.info("User already renewed through callback: {} ", request.getId());
            return null;
        }
        final AbstractTransactionInitRequest transactionInitRequest = DefaultTransactionInitRequestMapper.from(
                PlanRenewalRequest.builder().txnId(request.getId()).planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentGateway(request.getPaymentGateway())
                        .clientAlias(request.getClientAlias())
                        .build());
        final Transaction transaction = transactionManager.init(transactionInitRequest);
        final TransactionStatus initialStatus = transaction.getStatus();
        final IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> merchantPaymentRenewalService =
                BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), new ParameterizedTypeReference<IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>>() {
                });
        try {
            return merchantPaymentRenewalService.doRenewal(request);
        } finally {
            if (merchantPaymentRenewalService.supportsRenewalReconciliation()) {
                kafkaPublisherService.publishKafkaMessage(
                        PaymentReconciliationMessage.builder().paymentMethodId(common.getPaymentId(transactionManager.get(request.getId()))).paymentCode(transaction.getPaymentChannel().getId())
                                .paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                                .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid())
                                .originalAttemptSequence(request.getAttemptSequence() + 1).originalTransactionId(request.getId()).build());
            }
            final TransactionStatus finalStatus = transaction.getStatus();
            transactionManager.revision(AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(initialStatus).finalTransactionStatus(finalStatus)
                    .attemptSequence(request.getAttemptSequence()).originalTransactionId(request.getId()).lastSuccessTransactionId(transaction.getIdStr()).build());
            if (PaymentConstants.IAP_PAYMENT_METHODS.contains(transaction.getPaymentChannel().getId())) {
                Transaction oldTransaction = transactionManager.get(request.getId());
                eventPublisher.publishEvent(RecurringPaymentEvent.builder().transaction(oldTransaction).paymentEvent(PaymentEvent.UNSUBSCRIBE).build());
            }
        }
    }

    private boolean isEligibleForRenewal(Transaction oldTransaction) {
        try {
            String lastSuccessTransactionId = getLastSuccessTransactionId(transactionManager.get(oldTransaction.getIdStr()));
            if(StringUtils.isEmpty(lastSuccessTransactionId)) {
                log.info("The user subscription cannot be renewed because the LastSuccessTransactionId is not present in payment Renewal table");
                return true;
            }
            List<PaymentRenewal> paymentRenewalList = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), IPaymentRenewalDao.class).findByLastTransactionId(lastSuccessTransactionId);
            Optional<PaymentRenewal> newSuccessTransactionId = paymentRenewalList.stream().filter(paymentRenewal -> isTransactionSuccess(paymentRenewal.getTransactionId())).findFirst();
            if (!newSuccessTransactionId.isPresent()) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.info("The user subscription cannot be renewed: {} ", ex);
            return true;
        }
    }

    private boolean isTransactionSuccess(String transactionId) {
       try {
           Optional<Transaction> optionalTransaction = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).findById(transactionId);
           if (optionalTransaction.isPresent() && optionalTransaction.get().getStatus() == TransactionStatus.SUCCESS) {
               return true;
           }
           return false;
       } catch (Exception ex) {
           log.info("User cannot be renewed: {} ", ex);
           return false;
       }
    }

    public void addToPaymentRenewalMigration (MigrationTransactionRequest request) {
        final Calendar nextChargingDate = Calendar.getInstance();
        final Map<String, String> paymentMetaData = request.getPaymentMetaData();
        nextChargingDate.setTime(request.getNextChargingDate());
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request));
        paymentMetaData.put(MIGRATED, Boolean.TRUE.toString());
        paymentMetaData.put(TXN_ID, transaction.getIdStr());
        final IMerchantTransactionDetailsService merchantTransactionDetailsService = BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), IMerchantTransactionDetailsService.class);
        final MerchantTransaction merchantTransaction = merchantTransactionDetailsService.getMerchantTransactionDetails(paymentMetaData);
        merchantTransactionService.upsert(merchantTransaction);
        transactionManager.revision(MigrationTransactionRevisionRequest.builder().nextChargingDate(nextChargingDate).transaction(transaction).existingTransactionStatus(TransactionStatus.INPROGRESS)
                .finalTransactionStatus(transaction.getStatus()).build());
    }

    public WynkResponseEntity<WalletTopUpResponse> topUp (WalletTopUpRequest<?> request) {
        BeanLocatorFactory.getBean(CHARGING_FRAUD_DETECTION_CHAIN, IHandler.class).handle(request);
        final PaymentGateway paymentGateway = paymentMethodCache.get(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request.getPurchaseDetails()));
        kafkaPublisherService.publishKafkaMessage(
                PaymentReconciliationMessage.builder().paymentMethodId(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).paymentCode(transaction.getPaymentChannel().getId())
                        .transactionId(transaction.getIdStr()).paymentEvent(transaction.getType())
                        .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        return BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IWalletTopUpService<WalletTopUpResponse, WalletTopUpRequest<?>>>() {
        }).topUp(request);
    }

    private WynkResponseEntity<AbstractChargingStatusResponse> internalStatus (AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final PaymentGateway paymentGateway = transaction.getPaymentChannel();
        final IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> statusService =
                BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest>>() {
                });
        if (request.getPlanId() == 0) {
            request.setItemId(transaction.getItemId());
        } else {
            request.setPlanId(
                    transaction.getType() == in.wynk.common.enums.PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(transaction.getPlanId()).getLinkedFreePlanId() : transaction.getPlanId());
        }

        return statusService.status(request);
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

    private void publishEventsOnReconcileCompletion (TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(in.wynk.common.enums.PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

    private void publishBranchEvent (PaymentsBranchEvent paymentsBranchEvent) {
        eventPublisher.publishEvent(paymentsBranchEvent);
    }

    @Override
    @TransactionAware(txnId = "#request.tid", lock = false)
    public AbstractPaymentSettlementResponse settle (PaymentSettlementRequest request) {
        final Transaction transaction = TransactionContext.get();
        final String pgId = merchantTransactionService.getPartnerReferenceId(request.getTid());
        return BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(),
                new ParameterizedTypeReference<IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, ApsGatewaySettlementRequest>>() {
                }).settle(ApsGatewaySettlementRequest.builder().pgId(pgId).tid(transaction.getIdStr()).build());
    }

    @Override
    @TransactionAware(txnId = "#transactionId", lock = false)
    public BaseTDRResponse getTDR (String transactionId) {
        final Transaction transaction = TransactionContext.get();
        return BeanLocatorFactory.getBeanOrDefault(transaction.getPaymentChannel().getCode(), IMerchantTDRService.class, nope -> BaseTDRResponse.from(-1)).getTDR(transactionId);
    }


    @Override
    public void acknowledgeSubscription (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        final IMerchantIapSubscriptionAcknowledgementService acknowledgementService =
                BeanLocatorFactory.getBean(abstractPaymentAcknowledgementRequest.getPaymentCode(), IMerchantIapSubscriptionAcknowledgementService.class);
        acknowledgementService.acknowledgeSubscription(abstractPaymentAcknowledgementRequest);
    }

    @Override
    public void publishAsync (AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        final IMerchantIapSubscriptionAcknowledgementService acknowledgementService =
                BeanLocatorFactory.getBean(abstractPaymentAcknowledgementRequest.getPaymentCode(), IMerchantIapSubscriptionAcknowledgementService.class);
        acknowledgementService.publishAsync(abstractPaymentAcknowledgementRequest);
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
}