package in.wynk.payment.consumer;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.WebChargingRequestV2;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.service.PaymentGatewayManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class PaymentChargeConsumptionHandler implements PaymentChargeHandler<PaymentChargeRequestMessage> {
    private final PaymentGatewayManager manager;


    PaymentChargeConsumptionHandler (PaymentGatewayManager manager) {
        this.manager = manager;
    }

    @Override
    public void charge (PaymentChargeRequestMessage message) {
        AbstractPaymentChargingRequest request = new WebChargingRequestV2();
        try {
            BeanUtils.copyProperties(request, request);
        } catch (Exception e) {
            throw new WynkRuntimeException("Exception Occurred while converting message to charge Object");
        }
        AbstractPaymentChargingResponse res = manager.charge(request);
    }
}
