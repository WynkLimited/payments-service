package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.aps.request.mandate.cancel.CancelMandateRequest;
import in.wynk.payment.dto.request.AbstractCancelMandateRequest;
import in.wynk.payment.dto.aps.response.mandate.cancel.MandateCancellationResponse;
import in.wynk.payment.gateway.IPaymentMandateCancellation;
import in.wynk.payment.service.ITransactionManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsCancelMandateGatewayServiceImpl implements IPaymentMandateCancellation<AbstractCancelMandateRequest> {

    private final String CANCEL_MANDATE_ENDPOINT;
    private final ApsCommonGatewayService common;
    private final ITransactionManagerService transactionManager;

    public ApsCancelMandateGatewayServiceImpl(ITransactionManagerService transactionManager, String cancelMandateEndpoint, ApsCommonGatewayService common) {
        this.CANCEL_MANDATE_ENDPOINT = cancelMandateEndpoint;
        this.common = common;
        this.transactionManager = transactionManager;
    }

    @Override
    public void cancel (AbstractCancelMandateRequest request) {
        CancelMandateRequest mandateCancellationRequest = (CancelMandateRequest) request;
        final Transaction transaction = transactionManager.get(request.getTid());
        MandateCancellationResponse mandateCancellationResponse =
                common.exchange(transaction.getClientAlias(), CANCEL_MANDATE_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getMsisdn()), mandateCancellationRequest, MandateCancellationResponse.class);
        log.info("Mandate Cancellation Response from APS {}", mandateCancellationResponse);
    }
}
