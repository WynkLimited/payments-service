package in.wynk.payment.service;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.IWynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.FraudAware;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import in.wynk.payment.dto.manager.ChargingGatewayResponseWrapper;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.SyncTransactionRevisionRequest;
import in.wynk.payment.mapper.DefaultTransactionInitRequestMapper;
import in.wynk.queue.service.ISqsManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.PaymentConstants.VERSION_2;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayManager implements IPaymentChargingService<ChargingGatewayResponseWrapper<? extends AbstractChargingGatewayResponse>, AbstractChargingRequest<?>> {

    private final ICouponManager couponManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ISqsManagerService<Object> sqsManagerService;
    private final ITransactionManagerService transactionManager;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;

    @Override
    @FraudAware(name = CHARGING_FRAUD_DETECTION_CHAIN)
    public ChargingGatewayResponseWrapper<AbstractChargingGatewayResponse> charge(AbstractChargingRequest<?> request) {
        final PaymentCode pg = paymentMethodCache.get(request.getPurchaseDetails().getPaymentDetails().getPaymentId()).getPaymentCode();
        final Transaction transaction = transactionManager.init(DefaultTransactionInitRequestMapper.from(request.getPurchaseDetails()), request.getPurchaseDetails());
        final TransactionStatus existingStatus = transaction.getStatus();
        final IPaymentChargingService<AbstractChargingGatewayResponse, AbstractChargingRequest<?>> chargingService = BeanLocatorFactory.getBean(pg.getCode().concat(VERSION_2), new ParameterizedTypeReference<IPaymentChargingService<AbstractChargingGatewayResponse, AbstractChargingRequest<?>>>() {
        });
        try {
            final AbstractChargingGatewayResponse response = chargingService.charge(request);
            if (pg.isPreDebit()) {
                final TransactionStatus finalStatus = TransactionContext.get().getStatus();
                transactionManager.revision(SyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(existingStatus).finalTransactionStatus(finalStatus).build());
                exhaustCouponIfApplicable(existingStatus, finalStatus, transaction);
            }
            return ChargingGatewayResponseWrapper.builder().pgResponse(response).transaction(transaction).purchaseDetails(request.getPurchaseDetails()).build();
        } catch (Exception ex) {
            this.handleGatewayFailure(ex);
            throw ex;
        } finally {
            /** TODO:: Uncomment to make it part of of subsequent release for dropout notification
             eventPublisher.publishEvent(PurchaseInitEvent.builder().clientAlias(transaction.getClientAlias()).transactionId(transaction.getIdStr()).uid(transaction.getUid()).msisdn(transaction.getMsisdn()).productDetails(request.getPurchaseDetails().getProductDetails()).appDetails(request.getPurchaseDetails().getAppDetails()).sid(Optional.ofNullable(SessionContextHolder.getId())).build());**/
            sqsManagerService.publishSQSMessage(PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel().getId()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr()).itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
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

    private void handleGatewayFailure(Exception ex) {
        final Transaction transaction = TransactionContext.get();
        final PaymentErrorEvent.Builder eventBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
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

}
