package in.wynk.payment.gateway.aps;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.AbstractRechargeOrderRequest;
import in.wynk.payment.dto.request.RechargeOrderRequest;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.AbstractRechargeOrderResponse;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.gateway.IRechargeOrder;
import in.wynk.payment.gateway.aps.service.ApsCommonGatewayService;
import in.wynk.payment.gateway.aps.service.ApsOrderGatewayServiceImpl;
import in.wynk.vas.client.service.VasClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(ApsConstant.AIRTEL_PAY_STACK_RECHARGE)
public class ApsOrderGateway implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {
    private final IRechargeOrder<AbstractRechargeOrderResponse, AbstractRechargeOrderRequest> orderGateway;

    public ApsOrderGateway (@Value("${aps.payment.order.api}") String orderEndpoint, ApsCommonGatewayService commonGateway, VasClientService vasClientService) {
        this.orderGateway = new ApsOrderGatewayServiceImpl(orderEndpoint, commonGateway, vasClientService);
    }

    @Override
    public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
        orderGateway.order(RechargeOrderRequest.builder().build());
        final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargingService =
                BeanLocatorFactory.getBean("aps",
                        new ParameterizedTypeReference<IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>>() {
                        });

        chargingService.charge(request);
        return null;
    }
}
