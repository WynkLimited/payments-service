package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

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

    public ApsCancelMandateGatewayServiceImpl (ObjectMapper mapper, ITransactionManagerService transactionManager, IMerchantTransactionService merchantTransactionService, String cancelMandateEndpoint,
                                               ApsCommonGatewayService common, Gson gson) {
        this.gson = gson;
        this.mapper = mapper;
        this.common = common;
        this.transactionManager = transactionManager;
        this.CANCEL_MANDATE_ENDPOINT = cancelMandateEndpoint;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public void cancelRecurring (String transactionId) {
        try {
            MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transactionId);
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
        } catch (WynkRuntimeException ex) {
            log.error(APS_MANDATE_REVOKE_ERROR, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error(APS_MANDATE_REVOKE_ERROR, e.getMessage());
            throw new WynkRuntimeException(APS012, e);
        }
    }
}
