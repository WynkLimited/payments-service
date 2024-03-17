package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import in.wynk.payment.dto.payu.PayUPaymentRefundResponse;
import in.wynk.payment.dto.response.payu.PayURefundInitResponse;
import in.wynk.payment.gateway.IPaymentRefund;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.MultiValueMap;

@Slf4j
public class PayURefundGatewayImpl implements IPaymentRefund<PayUPaymentRefundResponse, PayUPaymentRefundRequest> {

    private final PayUCommonGateway common;
    private final ApplicationEventPublisher eventPublisher;

    public PayURefundGatewayImpl(PayUCommonGateway common, ApplicationEventPublisher eventPublisher){
        this.common= common;
        this.eventPublisher= eventPublisher;
    }

    @Override
    public PayUPaymentRefundResponse doRefund(PayUPaymentRefundRequest request) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        PayUPaymentRefundResponse.PayUPaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PayUPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId()).itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn()).paymentEvent(refundTransaction.getType());
        try {
            MultiValueMap<String, String>
                    refundDetails = common.buildPayUInfoRequest(refundTransaction.getClientAlias(), PayUCommand.CANCEL_REFUND_TRANSACTION.getCode(), request.getAuthPayUId(), refundTransaction.getIdStr(), String.valueOf(refundTransaction.getAmount()));
            PayURefundInitResponse refundResponse = common.exchange(common.INFO_API, refundDetails, new TypeReference<PayURefundInitResponse>() {
            });
            if (refundResponse.getStatus() == 0) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(refundResponse.getStatus())).description(refundResponse.getMessage()).build();
                eventPublisher.publishEvent(errorEvent);
            } else {
                refundResponseBuilder.authPayUId(refundResponse.getAuthPayUId()).requestId(refundResponse.getRequestId());
            }
        } catch (WynkRuntimeException ex) {
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
        }
        return refundResponseBuilder.build();
    }
}
