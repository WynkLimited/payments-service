package in.wynk.payment.consumer;

import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.service.PaymentGatewayManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class PaymentChargeConsumptionHandler implements PaymentChargeHandler<AbstractPaymentChargingRequest> {
    private final PaymentGatewayManager manager;


    PaymentChargeConsumptionHandler (PaymentGatewayManager manager) {
        this.manager = manager;
    }

    @Override
    public void charge (AbstractPaymentChargingRequest request) {
        manager.charge(request);
    }
}
