package in.wynk.payment.gateway.aps;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.AbstractRechargeOrderRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.RechargeOrderRequest;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.AbstractRechargeOrderResponse;
import in.wynk.payment.dto.response.RechargeOrderResponse;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.gateway.IPaymentInstrumentsProxy;
import in.wynk.payment.gateway.IRechargeOrder;
import in.wynk.payment.gateway.aps.service.ApsCommonGatewayService;
import in.wynk.payment.gateway.aps.service.ApsEligibilityGatewayServiceImpl;
import in.wynk.payment.gateway.aps.service.ApsOrderGatewayServiceImpl;
import in.wynk.payment.gateway.aps.service.ApsPaymentOptionsServiceImpl;
import in.wynk.payment.service.IExternalPaymentEligibilityService;
import in.wynk.payment.service.IMerchantTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(ApsConstant.AIRTEL_PAY_STACK_V2)
public class ApsOrderGateway implements IExternalPaymentEligibilityService, IPaymentInstrumentsProxy<PaymentOptionsPlanEligibilityRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload>, IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

    private final IRechargeOrder<AbstractRechargeOrderResponse, AbstractRechargeOrderRequest> orderGateway;
    private final IExternalPaymentEligibilityService eligibilityGateway;
    private final IPaymentInstrumentsProxy<PaymentOptionsPlanEligibilityRequest> payOptionsGateway;
    private final IMerchantTransactionService merchantTransactionService;

    public ApsOrderGateway (@Value("${aps.payment.order.api}") String orderEndpoint, @Value("${aps.payment.option.api}") String payOptionEndpoint, ApsCommonGatewayService commonGateway, IMerchantTransactionService merchantTransactionService) {
        this.orderGateway = new ApsOrderGatewayServiceImpl(orderEndpoint, commonGateway);
        this.eligibilityGateway = new ApsEligibilityGatewayServiceImpl();
        this.payOptionsGateway = new ApsPaymentOptionsServiceImpl(payOptionEndpoint, commonGateway);
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
        RechargeOrderResponse orderResponse = (RechargeOrderResponse) orderGateway.order(RechargeOrderRequest.builder().build());
        request.setOrderId(orderResponse.getOrderId());
        final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargingService =
                BeanLocatorFactory.getBean("aps",
                        new ParameterizedTypeReference<IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>>() {
                        });

        return chargingService.charge(request);
    }

    @Override
    public boolean isEligible(PaymentMethod entity, PaymentOptionsPlanEligibilityRequest request) {
        return eligibilityGateway.isEligible(entity, request);
    }

    @Override
    public AbstractPaymentInstrumentsProxy<?, ?> load(PaymentOptionsPlanEligibilityRequest request) {
        return payOptionsGateway.load(request);
    }

    @Override
    public AbstractPaymentCallbackResponse handle (ApsCallBackRequestPayload callbackRequest) {
        final IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest> callbackService =
                BeanLocatorFactory.getBean("aps", new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest>>() {
                });
        return callbackService.handle(callbackRequest);
    }

    @Override
    public ApsCallBackRequestPayload parse (Map<String, Object> payload) {
        final IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest> callbackService =
                BeanLocatorFactory.getBean("aps", new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest>>() {
                });

        ApsCallBackRequestPayload response = (ApsCallBackRequestPayload) callbackService.parse(payload);
        try {
            String txnId = merchantTransactionService.findTransactionId(response.getOrderId());
            response.setOrderId(txnId);
        } catch (Exception e) {
            log.error("Exception occuerred while finding orderId in mercahnt table for order created with APS");
            throw new WynkRuntimeException(PaymentErrorType.PAY049, e);
        }
        return response;
    }
}
