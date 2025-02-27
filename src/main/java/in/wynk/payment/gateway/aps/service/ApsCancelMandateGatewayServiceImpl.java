package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.aps.request.mandate.cancel.CancelMandateRequest;
import in.wynk.payment.dto.aps.response.mandate.cancel.MandateCancellationResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.service.ICancellingRecurringService;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.subscription.common.message.CancelMandateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentErrorType.APS012;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_MANDATE_REVOKE_ERROR;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsCancelMandateGatewayServiceImpl implements ICancellingRecurringService {

    private final String CANCEL_MANDATE_ENDPOINT;
    private final ApsCommonGatewayService common;
    private final ITransactionManagerService transactionManager;
    private final IMerchantTransactionService merchantTransactionService;
    private final Gson gson;
    private final ObjectMapper mapper;
    private final IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService;

    public ApsCancelMandateGatewayServiceImpl (ObjectMapper mapper, ITransactionManagerService transactionManager, IMerchantTransactionService merchantTransactionService, String cancelMandateEndpoint,
                                               ApsCommonGatewayService common, Gson gson, IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService) {
        this.gson = gson;
        this.mapper = mapper;
        this.common = common;
        this.transactionManager = transactionManager;
        this.CANCEL_MANDATE_ENDPOINT = cancelMandateEndpoint;
        this.merchantTransactionService = merchantTransactionService;
        this.kafkaPublisherService = kafkaPublisherService;
    }

    @Override
    public void cancelRecurring (String transactionId, PaymentEvent event) {
        try {
            MerchantTransaction merchantTransaction = getMerchantData(transactionId);
            //retry getting data from database
            if (Objects.isNull(merchantTransaction)) {
                merchantTransaction = getMerchantData(transactionId);
                ;
            }
            if (Objects.isNull(merchantTransaction)) {
                throw new WynkRuntimeException(APS012, "could not find mandate id in merchant Table for transaction Id" + transactionId);
            }
            ApsChargeStatusResponse[] apsChargeStatusResponses = mapper.convertValue(merchantTransaction.getResponse(), ApsChargeStatusResponse[].class);
            if (apsChargeStatusResponses.length == 0) {
                throw new WynkRuntimeException(APS012, "data is corrupted in merchant table for transaction Id {}" + transactionId);
            }
            ApsChargeStatusResponse apsChargeStatusResponse = apsChargeStatusResponses[0];
            CancelMandateRequest mandateCancellationRequest = CancelMandateRequest.builder()
                    .mandateTransactionId(apsChargeStatusResponse.getMandateId()).cancellationRequestId(transactionId)
                    .paymentGateway(apsChargeStatusResponse.getPaymentRoutedThrough()).paymentMode(apsChargeStatusResponse.getPaymentMode())
                    .build();
            final Transaction transaction = transactionManager.get(transactionId);
            MandateCancellationResponse mandateCancellationResponse =
                    common.exchange(transaction.getClientAlias(), CANCEL_MANDATE_ENDPOINT, HttpMethod.POST, transaction.getMsisdn(), mandateCancellationRequest,
                            MandateCancellationResponse.class);
            AnalyticService.update(PaymentConstants.MANDATE_REVOKE_RESPONSE, gson.toJson(mandateCancellationResponse));
            CancelMandateEvent mandateEvent= CancelMandateEvent.builder().apsCancellationResponse(CancelMandateEvent.MandateCancellationResponse.builder()
                            .mandateTransactionId(mandateCancellationResponse.getMandateTransactionId()).
                            cancellationRequestId(mandateCancellationResponse.getCancellationRequestId())
                            .autopayStatus(CancelMandateEvent.MandateCancellationResponse.AutopayStatus.builder().siRegistrationStatus(mandateCancellationResponse.getAutopayStatus().getSiRegistrationStatus()).build()).build())
                    .planId(transaction.getPlanId())
                    .paymentCode(transaction.getPaymentChannel().getCode().toUpperCase())
                    .msisdn(transaction.getMsisdn())
                    .uid(transaction.getUid())
                    .paymentEvent(event)
                    .build();
            kafkaPublisherService.publish(mandateEvent);
        } catch (WynkRuntimeException ex) {
            log.error(APS_MANDATE_REVOKE_ERROR, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error(APS_MANDATE_REVOKE_ERROR, e.getMessage());
            throw new WynkRuntimeException(APS012, e);
        }
    }


    private MerchantTransaction getMerchantData (String id) {
        try {
            return merchantTransactionService.getMerchantTransaction(id);
        } catch (Exception e) {
            log.error("Exception occurred while getting data for tid {} from merchant table: {}", id, e.getMessage());
            return null;
        }
    }
}
