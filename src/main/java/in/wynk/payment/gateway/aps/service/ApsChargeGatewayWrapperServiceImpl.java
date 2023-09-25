package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;

/**
 * @author Nishesh Pandey
 */
public class ApsChargeGatewayWrapperServiceImpl implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {
    private final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargeGateway;
    private final PaymentCachingService paymentCachingService;
    public ApsChargeGatewayWrapperServiceImpl (String upiChargeEndpoint, String commonChargeEndpoint, PaymentMethodCachingService paymentMethodCachingService, ApsCommonGatewayService commonGatewayService, PaymentCachingService paymentCachingService) {
        this.chargeGateway = new ApsChargeGatewayServiceImpl(upiChargeEndpoint, commonChargeEndpoint, paymentMethodCachingService, commonGatewayService);
        this.paymentCachingService = paymentCachingService;
    }


    @Override
    public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
        final PlanDTO planDTO = paymentCachingService.getPlan(request.getProductDetails().getId());
        if(planDTO.isRechargePlan()){
            //Create request for order service
        }
        chargeGateway.charge(request);
        return null;
    }
}
