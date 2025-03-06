package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.enums.PaymentEvent;
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
import in.wynk.payment.service.ITransactionManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.MultiValueMap;

import java.util.EnumSet;
import java.util.Optional;

@Slf4j
public class PayURefundGatewayImpl implements IPaymentRefund<PayUPaymentRefundResponse, PayUPaymentRefundRequest> {

    private final PayUCommonGateway common;
    private final ApplicationEventPublisher eventPublisher;
    ITransactionManagerService transactionManagerService;

    public PayURefundGatewayImpl (PayUCommonGateway common, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManagerService){
        this.common= common;
        this.eventPublisher= eventPublisher;
        this.transactionManagerService = transactionManagerService;
    }

    @Override
    public PayUPaymentRefundResponse doRefund(PayUPaymentRefundRequest request) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        MerchantTransactionEvent.Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        PayUPaymentRefundResponse.PayUPaymentRefundResponseBuilder<?, ?> refundResponseBuilder =
                PayUPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId())
                        .itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn())
                        .paymentEvent(refundTransaction.getType());
        try {
            MultiValueMap<String, String>
                    refundDetails =
                    common.buildPayUInfoRequest(refundTransaction.getClientAlias(), PayUCommand.CANCEL_REFUND_TRANSACTION.getCode(), request.getAuthPayUId(), refundTransaction.getIdStr(),
                            String.valueOf(refundTransaction.getAmount()));
            merchantTransactionBuilder.request(refundDetails);
            PayURefundInitResponse refundResponse = common.exchange(common.INFO_API, refundDetails, new TypeReference<PayURefundInitResponse>() {
            });
            if (refundResponse.getStatus() == 0) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                PaymentErrorEvent errorEvent =
                        PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(refundResponse.getStatus())).description(refundResponse.getMessage()).build();
                eventPublisher.publishEvent(errorEvent);
            } else {
                refundResponseBuilder.authPayUId(refundResponse.getAuthPayUId()).requestId(refundResponse.getRequestId());
                merchantTransactionBuilder.externalTransactionId(refundResponse.getRequestId()).response(refundResponse).build();
            }
        } catch (WynkRuntimeException ex) {
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
        }
        Transaction transaction = transactionManagerService.get(request.getOriginalTransactionId());
        if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
            common.syncChargingTransactionFromSource(transaction, Optional.empty());
        } else {
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return refundResponseBuilder.build();
    }
}
