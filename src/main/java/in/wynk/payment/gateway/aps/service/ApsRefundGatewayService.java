package in.wynk.payment.gateway.aps.service;


import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.refund.ApsExternalPaymentRefundRequest;
import in.wynk.payment.dto.aps.response.refund.ApsExternalPaymentRefundStatusResponse;
import in.wynk.payment.service.IMerchantPaymentRefundService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsRefundGatewayService implements IMerchantPaymentRefundService<ApsPaymentRefundResponse, ApsPaymentRefundRequest> {

    private final String REFUND_ENDPOINT;
    private final ApsCommonGatewayService common;
    private final ApplicationEventPublisher eventPublisher;

    public ApsRefundGatewayService (String refundEndpoint, ApplicationEventPublisher eventPublisher, ApsCommonGatewayService common){
        this.common= common;
        this.eventPublisher= eventPublisher;
        this.REFUND_ENDPOINT = refundEndpoint;
    }


    @Override
    public WynkResponseEntity<ApsPaymentRefundResponse> refund (ApsPaymentRefundRequest request) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final Transaction refundTransaction = TransactionContext.get();
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        final WynkResponseEntity.WynkResponseEntityBuilder<ApsPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        final ApsPaymentRefundResponse.ApsPaymentRefundResponseBuilder<?, ?> refundResponseBuilder =
                ApsPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId())
                        .itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn())
                        .paymentEvent(refundTransaction.getType());
        try {
            final ApsExternalPaymentRefundRequest refundRequest =
                    ApsExternalPaymentRefundRequest.builder().refundAmount(String.valueOf(refundTransaction.getAmount())).pgId(request.getPgId()).postingId(refundTransaction.getIdStr()).build();
            ApsExternalPaymentRefundStatusResponse body =
                    common.exchange(REFUND_ENDPOINT, HttpMethod.POST,common.getLoginId(refundTransaction.getMsisdn()),refundRequest, ApsExternalPaymentRefundStatusResponse.class);

            mBuilder.request(refundRequest);
            mBuilder.response(body);
            mBuilder.externalTransactionId(body.getRefundId());
            refundResponseBuilder.requestId(body.getRefundId());
            if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (WynkRuntimeException ex) {
            final PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            responseBuilder.success(false).status(ex.getErrorType().getHttpResponseStatusCode())
                    .error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(mBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }
}
