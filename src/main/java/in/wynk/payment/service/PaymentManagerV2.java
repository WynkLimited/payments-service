package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingResponse;

/**
 * @author Nishesh Pandey
 */
public class PaymentManagerV2 {

  /* // @Override
    public WynkResponseEntity<AbstractChargingResponse> charge(AbstractChargingRequest<?> request) {
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
            *//** TODO:: Uncomment to make it part of of subsequent release for dropout notification
             eventPublisher.publishEvent(PurchaseInitEvent.builder().clientAlias(transaction.getClientAlias()).transactionId(transaction.getIdStr()).uid(transaction.getUid()).msisdn(transaction
             .getMsisdn()).productDetails(request.getPurchaseDetails().getProductDetails()).appDetails(request.getPurchaseDetails().getAppDetails()).sid(Optional.ofNullable(SessionContextHolder
             .getId())).build());**//*
            sqsManagerService.publishSQSMessage(
                    PaymentReconciliationMessage.builder().paymentCode(transaction.getPaymentChannel().getId()).paymentEvent(transaction.getType()).transactionId(transaction.getIdStr())
                            .itemId(transaction.getItemId()).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).build());
            //publishBranchEvent(PaymentsBranchEvent.<EventsWrapper>builder().eventName(PAYMENT_CHARGING_EVENT).data(getEventsWrapperBuilder(transaction, Optional.ofNullable(request
            // .getPurchaseDetails())).build()).build());
        }
    }*/
}
