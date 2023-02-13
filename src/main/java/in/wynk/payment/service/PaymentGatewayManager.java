package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.IWynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.FraudAware;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.ClientCallbackEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.request.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.common.response.ChargingResponseWrapper;
import in.wynk.payment.dto.common.response.PaymentStatusWrapper;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
import in.wynk.queue.service.ISqsManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;
import static in.wynk.payment.core.constant.PaymentConstants.VERSION_2;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayManager
        implements IPaymentChargingService<ChargingResponseWrapper<?>, AbstractChargingRequest<?>>, IPaymentStatus<PaymentStatusWrapper>,
        IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionReconciliationStatusRequest>, IPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

    private final ICouponManager couponManager;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ISqsManagerService<Object> sqsManagerService;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;


    @FraudAware(name = CHARGING_FRAUD_DETECTION_CHAIN)
    public AbstractCoreChargingResponse chargeV2 (AbstractChargingRequestV2 request) {
        final PaymentGateway paymentGateway = paymentMethodCache.get(request.getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request), request);
        final IPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> chargingService =
                BeanLocatorFactory.getBean(paymentGateway.getCode().concat(VERSION_2),
                        new ParameterizedTypeReference<IPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>>() {
                        });

      // final AbstractCoreChargingResponse response = chargingService.chargev2(request);

        return null;
    }

    @Override
    @FraudAware(name = CHARGING_FRAUD_DETECTION_CHAIN)
    public ChargingResponseWrapper<?> charge (AbstractChargingRequest<?> request) {
        final PaymentGateway pg = paymentMethodCache.get(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request.getPurchaseDetails()), request.getPurchaseDetails());
        final TransactionStatus existingStatus = transaction.getStatus();
        final IPaymentChargingService<AbstractPaymentChargingResponse, AbstractChargingRequest<?>> chargingService =
                BeanLocatorFactory.getBean(pg.getCode().concat(VERSION_2), new ParameterizedTypeReference<IPaymentChargingService<AbstractPaymentChargingResponse, AbstractChargingRequest<?>>>() {
                });
        try {
            final AbstractPaymentChargingResponse response = chargingService.charge(request);
            if (pg.isPreDebit()) {
                AbstractTransactionRevisionRequest abstractTransactionRevisionRequest =
                        SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(transaction.getStatus()).build();
                reviseTransactionAndExhaustCoupon(transaction, existingStatus, abstractTransactionRevisionRequest);
            }
            return ChargingResponseWrapper.builder().pgResponse(response).transaction(transaction).purchaseDetails(request.getPurchaseDetails()).build();
        } catch (Exception ex) {
            this.handleGatewayFailure(ex);
            throw ex;
        } finally {
            /** TODO:: Uncomment to make it part of of subsequent release for dropout notification
             eventPublisher.publishEvent(PurchaseInitEvent.builder().clientAlias(transaction.getClientAlias()).transactionId(transaction.getIdStr()).uid(transaction.getUid()).msisdn(transaction
             .getMsisdn()).productDetails(request.getPurchaseDetails().getProductDetails()).appDetails(request.getPurchaseDetails().getAppDetails()).sid(Optional.ofNullable(SessionContextHolder
             .getId())).build());**/
            sqsManagerService.publishSQSMessage(
                    PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel().getId()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                            .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
        }
    }

    @TransactionAware(txnId = "#transactionId", lock = false)
    @Override
    public PaymentStatusWrapper status (Transaction transaction) {
        PaymentGateway paymentGateway = transaction.getPaymentChannel();
        IPaymentStatus<PaymentStatusWrapper> paymentStatus = BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IPaymentStatus<PaymentStatusWrapper>>() {
        });
        return paymentStatus.status(transaction);
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

    @Override
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
                        .transactionId(renewalChargingTransactionReconciliationStatusRequest.getOriginalTransactionId());
            }
            reviseTransactionAndExhaustCoupon(transaction, existingStatus, builder.build());
            publishEventsOnReconcileCompletion(existingStatus, finalStatus, transaction);
            //publishBranchEvent(PaymentsBranchEvent.<EventsWrapper>builder().eventName(PAYMENT_RECONCILE_EVENT).data(getEventsWrapperBuilder(transaction, TransactionContext.getPurchaseDetails())
            // .extTxnId(request.getExtTxnId()).build()).build());
        }
    }

    private void publishEventsOnReconcileCompletion (TransactionStatus existingStatus, TransactionStatus finalStatus, Transaction transaction) {
        eventPublisher.publishEvent(PaymentReconciledEvent.from(transaction));
        if (!EnumSet.of(in.wynk.common.enums.PaymentEvent.REFUND).contains(transaction.getType()) && existingStatus != TransactionStatus.SUCCESS && finalStatus == TransactionStatus.SUCCESS) {
            eventPublisher.publishEvent(ClientCallbackEvent.from(transaction));
        }
    }

    private WynkResponseEntity<AbstractChargingStatusResponse> internalStatus (AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final PaymentGateway paymentGateway = transaction.getPaymentChannel();
        final IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> statusService =
                BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest>>() {
                });
        request.setPlanId(
                transaction.getType() == in.wynk.common.enums.PaymentEvent.TRIAL_SUBSCRIPTION ? cachingService.getPlan(transaction.getPlanId()).getLinkedFreePlanId() : transaction.getPlanId());
        return statusService.status(request);
    }

    private void reviseTransactionAndExhaustCoupon (Transaction transaction, TransactionStatus existingStatus,
                                                    AbstractTransactionRevisionRequest abstractTransactionRevisionRequest) {
        transactionManager.revision(abstractTransactionRevisionRequest);
        exhaustCouponIfApplicable(existingStatus, transaction.getStatus(), transaction);
    }
}
