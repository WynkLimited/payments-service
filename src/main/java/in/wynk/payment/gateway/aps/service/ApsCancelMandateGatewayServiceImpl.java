package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.dto.aps.request.mandate.cancel.CancelMandateRequest;
import in.wynk.payment.dto.request.AbstractCancelMandateRequest;
import in.wynk.payment.dto.aps.response.mandate.cancel.MandateCancellationResponse;
import in.wynk.payment.gateway.IPaymentMandateCancellation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsCancelMandateGatewayServiceImpl implements IPaymentMandateCancellation<AbstractCancelMandateRequest> {

    private final String CANCEL_MANDATE_ENDPOINT;
    private final ApsCommonGatewayService common;

    public ApsCancelMandateGatewayServiceImpl(String cancelMandateEndpoint, ApsCommonGatewayService common) {
        this.CANCEL_MANDATE_ENDPOINT = cancelMandateEndpoint;
        this.common = common;
    }

    @Override
    public void cancel (AbstractCancelMandateRequest request) {
        CancelMandateRequest mandateCancellationRequest = (CancelMandateRequest) request;
        MandateCancellationResponse mandateCancellationResponse =
                common.exchange(CANCEL_MANDATE_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getMsisdn()), mandateCancellationRequest, MandateCancellationResponse.class);
        log.info("Mandate Cancellation Response from APS {}", mandateCancellationResponse);
    }
}
