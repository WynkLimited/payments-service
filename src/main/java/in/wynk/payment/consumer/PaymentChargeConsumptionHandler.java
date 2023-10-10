package in.wynk.payment.consumer;

import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.event.PaymentChargeEvent;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.aps.kafka.Message;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.S2SChargingRequestV2;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.presentation.IPaymentPresentationV2;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import in.wynk.payment.presentation.dto.charge.upi.IntentSeamlessUpiPaymentChargingResponse;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.wynkservice.api.service.WynkServiceDetailsCachingService;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class PaymentChargeConsumptionHandler implements PaymentChargeHandler<PaymentChargeRequestMessage> {
    private final PaymentGatewayManager manager;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final WynkServiceDetailsCachingService wynkServiceDetailsCachingService;
    public static Map<String, String> map = Collections.singletonMap("airteltv","airtelxstream");

    PaymentChargeConsumptionHandler (PaymentGatewayManager manager, ApplicationEventPublisher eventPublisher, PaymentMethodCachingService paymentMethodCachingService,
                                     WynkServiceDetailsCachingService wynkServiceDetailsCachingService) {
        this.manager = manager;
        this.eventPublisher = eventPublisher;
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.wynkServiceDetailsCachingService = wynkServiceDetailsCachingService;
    }

    @Override
    public void charge (PaymentChargeRequestMessage requestMessage) {
        Message request = requestMessage.getMessage();
        //TODO: Integrate payment options and then get first eligible payment method id
        WynkService resp = wynkServiceDetailsCachingService.get(requestMessage.getServiceId());
        S2SChargingRequestV2 chargingRequestV2 = getData(map.getOrDefault(requestMessage.getServiceId(), resp.getLinkedClient()), request);

        final WynkResponseEntity<PaymentChargingResponse> responseEntity =
                BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentationV2<PaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>>() {
                }).transform(() -> Pair.of(chargingRequestV2, manager.charge(chargingRequestV2)));
        IntentSeamlessUpiPaymentChargingResponse intentResponse = (IntentSeamlessUpiPaymentChargingResponse) responseEntity.getBody().getData();
        eventPublisher.publishEvent(toPaymentChargeEvent(requestMessage, intentResponse));
    }

    @ClientAware(clientAlias = "#clientAlias")
    private S2SChargingRequestV2 getData (String clientAlias, Message request) {
        return S2SChargingRequestV2.builder().paymentDetails(request.getPaymentDetails()).productDetails(request.getProductDetails()).geoLocation(request.getGeoLocation())
                .appDetails(request.getAppDetails()).userDetails(request
                        .getUserDetails()).build();
    }

    private PaymentChargeEvent toPaymentChargeEvent (PaymentChargeRequestMessage requestMessage, IntentSeamlessUpiPaymentChargingResponse intentResponse) {
        PaymentGateway paymentGateway = paymentMethodCachingService.get(requestMessage.getMessage().getPaymentDetails().getPaymentId()).getPaymentCode();
        return PaymentChargeEvent.builder().transactionId(intentResponse.getTid()).transactionType(intentResponse.getTransactionType()).transactionStatus(intentResponse.getTransactionStatus())
                .paymentGatewayCode(paymentGateway.getCode()).to(requestMessage.getMessage().getFrom()).from(requestMessage.getMessage().getTo())
                .campaignId(requestMessage.getMessage().getCampaignId()).orgId(requestMessage.getOrgId())
                .serviceId(requestMessage.getServiceId()).sessionId(
                        requestMessage.getSessionId()).requestId(requestMessage.getRequestId()).planId(requestMessage.getMessage().getProductDetails().getId()).deeplink(intentResponse.getDeepLink())
                .trialOpted(requestMessage.getMessage().getPaymentDetails().isTrialOpted())
                .mandate(requestMessage.getMessage().isMandateSupported()).clientAlias(requestMessage.getMessage().getClientDetails().getAlias()).build();
    }
}
