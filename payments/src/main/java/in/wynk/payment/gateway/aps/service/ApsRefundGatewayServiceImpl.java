package in.wynk.payment.gateway.aps.service;


import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.status.refund.ApsPaymentRefundRequest;
import in.wynk.payment.dto.aps.response.status.refund.ApsRefundStatusResponse;
import in.wynk.payment.gateway.IPaymentRefund;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsRefundGatewayServiceImpl implements IPaymentRefund<ApsPaymentRefundResponse, in.wynk.payment.dto.ApsPaymentRefundRequest> {

    private final String REFUND_ENDPOINT;
    private final ApsCommonGatewayService common;
    private final ApplicationEventPublisher eventPublisher;

    public ApsRefundGatewayServiceImpl(String refundEndpoint, ApplicationEventPublisher eventPublisher, ApsCommonGatewayService common) {
        this.common = common;
        this.eventPublisher = eventPublisher;
        this.REFUND_ENDPOINT = refundEndpoint;
    }


    @Override
    public ApsPaymentRefundResponse doRefund(in.wynk.payment.dto.ApsPaymentRefundRequest request) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final Transaction refundTransaction = TransactionContext.get();
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        final ApsPaymentRefundResponse.ApsPaymentRefundResponseBuilder<?, ?> refundResponseBuilder =
                ApsPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId())
                        .itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn())
                        .paymentEvent(refundTransaction.getType());
        try {
            final ApsPaymentRefundRequest refundRequest =
                    ApsPaymentRefundRequest.builder().refundAmount(String.valueOf(refundTransaction.getAmount())).pgId(request.getPgId()).postingId(refundTransaction.getIdStr()).overrideTimeLimit(true).build();
            ApsRefundStatusResponse body =
                    common.exchange(refundTransaction.getClientAlias(), REFUND_ENDPOINT, HttpMethod.POST, refundTransaction.getMsisdn(), refundRequest, ApsRefundStatusResponse.class);

            mBuilder.request(refundRequest);
            mBuilder.response(body);
            mBuilder.externalTransactionId(body.getPgId());
            refundResponseBuilder.requestId(body.getRefundId());
            if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (WynkRuntimeException ex) {
            final PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(mBuilder.build());
        }
        return refundResponseBuilder.build();
    }
}
