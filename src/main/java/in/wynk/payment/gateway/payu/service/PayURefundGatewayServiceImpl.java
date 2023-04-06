package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
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
import in.wynk.payment.service.IMerchantPaymentRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.MultiValueMap;

@Slf4j
public class PayURefundGatewayServiceImpl implements IMerchantPaymentRefundService<PayUPaymentRefundResponse, PayUPaymentRefundRequest> {

    private final PayUCommonGatewayService common;
    private final ApplicationEventPublisher eventPublisher;

    public PayURefundGatewayServiceImpl(PayUCommonGatewayService common, ApplicationEventPublisher eventPublisher){
        this.common= common;
        this.eventPublisher= eventPublisher;
    }

    @Override
    public WynkResponseEntity<PayUPaymentRefundResponse> refund (PayUPaymentRefundRequest request) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        MerchantTransactionEvent.Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        WynkResponseEntity.WynkResponseEntityBuilder<PayUPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        PayUPaymentRefundResponse.PayUPaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PayUPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId()).itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn()).paymentEvent(refundTransaction.getType());
        try {
            MultiValueMap<String, String>
                    refundDetails = common.buildPayUInfoRequest(refundTransaction.getClientAlias(), PayUCommand.CANCEL_REFUND_TRANSACTION.getCode(), request.getAuthPayUId(), refundTransaction.getIdStr(), String.valueOf(refundTransaction.getAmount()));
            merchantTransactionBuilder.request(refundDetails);
            PayURefundInitResponse refundResponse = common.exchange(common.INFO_API, refundDetails, new TypeReference<PayURefundInitResponse>() {
            });
            if (refundResponse.getStatus() == 0) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(refundResponse.getStatus())).description(refundResponse.getMessage()).build();
                responseBuilder.success(false).error(StandardBusinessErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
                eventPublisher.publishEvent(errorEvent);
            } else {
                refundResponseBuilder.authPayUId(refundResponse.getAuthPayUId()).requestId(refundResponse.getRequestId());
                merchantTransactionBuilder.externalTransactionId(refundResponse.getRequestId()).response(refundResponse).build();
            }
        } catch (WynkRuntimeException ex) {
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            responseBuilder.success(false).status(ex.getErrorType().getHttpResponseStatusCode()).error(
                    TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }
}
