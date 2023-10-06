package in.wynk.payment.consumer;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.event.PaymentChargeEvent;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.WebChargingRequestV2;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.presentation.IPaymentPresentationV2;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import in.wynk.payment.presentation.dto.charge.upi.IntentSeamlessUpiPaymentChargingResponse;
import in.wynk.payment.service.PaymentGatewayManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class PaymentChargeConsumptionHandler implements PaymentChargeHandler<PaymentChargeRequestMessage> {
    private final PaymentGatewayManager manager;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMethodCachingService paymentMethodCachingService;

    PaymentChargeConsumptionHandler (PaymentGatewayManager manager, ApplicationEventPublisher eventPublisher, PaymentMethodCachingService paymentMethodCachingService) {
        this.manager = manager;
        this.eventPublisher = eventPublisher;
        this.paymentMethodCachingService = paymentMethodCachingService;
    }

    @Override
    public void charge (PaymentChargeRequestMessage message) {
        AbstractPaymentChargingRequest request = new WebChargingRequestV2();

        try {
            BeanUtils.copyProperties(message, request);
        } catch (Exception e) {
            throw new WynkRuntimeException("Exception Occurred while converting message to charge Object");
        }
        final WynkResponseEntity<PaymentChargingResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentationV2<PaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>>() {
                }).transform(() -> Pair.of(request, manager.charge(request)));
        IntentSeamlessUpiPaymentChargingResponse intentResponse = (IntentSeamlessUpiPaymentChargingResponse)responseEntity.getBody().getData();
        eventPublisher.publishEvent(toPaymentChargeEvent(message, intentResponse));
    }

    private PaymentChargeEvent toPaymentChargeEvent (PaymentChargeRequestMessage message, IntentSeamlessUpiPaymentChargingResponse intentResponse) {
        PaymentGateway paymentGateway = paymentMethodCachingService.get(message.getPaymentDetails().getPaymentId()).getPaymentCode();
        return PaymentChargeEvent.builder().transactionId(intentResponse.getTid()).transactionType(intentResponse.getTransactionType()).transactionStatus(intentResponse.getTransactionStatus())
                .paymentGatewayCode(paymentGateway.getCode()).to(message.getFrom()).from(message.getTo()).campaignId(message.getCampaignId()).orgId(message.getOrgId())
                .serviceId(message.getServiceId()).sessionId(
                        message.getSessionId()).requestId(message.getRequestId()).planId(message.getProductDetails().getId()).deeplink(intentResponse.getDeepLink()).trialOpted(message.isTrialOpted())
                .mandate(message.isMandateSupported()).clientAlias(message.getClientDetails().getAlias()).build();
    }
}
