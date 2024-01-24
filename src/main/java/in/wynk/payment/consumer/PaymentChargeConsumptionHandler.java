package in.wynk.payment.consumer;

import com.datastax.driver.core.utils.UUIDs;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentGroupCachingService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.WhatsappPaymentOptionsRequest;
import in.wynk.payment.dto.aps.kafka.PayChargeReqMessage;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.gateway.upi.UpiIntentChargingResponse;
import in.wynk.payment.dto.request.DefaultPaymentOptionRequest;
import in.wynk.payment.dto.request.WhatsAppChargeRequest;
import in.wynk.payment.dto.request.WhatsappSessionDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.common.UpiDetails;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.event.WaPayChargeRespEvent;
import in.wynk.payment.event.common.*;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.utils.TaxUtils;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.wynkservice.api.service.WynkServiceDetailsCachingService;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

import static in.wynk.payment.constant.UpiConstants.UPI;
import static in.wynk.payment.constant.UpiConstants.UPI_PREFIX;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class PaymentChargeConsumptionHandler implements PaymentChargeHandler<PaymentChargeRequestMessage> {

    private final String topic;
    private final TaxUtils taxUtils;
    private final PaymentGatewayManager manager;
    private final IPaymentOptionServiceV2 payOptionService;
    private final PaymentCachingService paymentCachingService;
    private final PaymentGroupCachingService paymentGroupCachingService;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IKafkaEventPublisher<String, WaPayChargeRespEvent> kafkaPublisher;
    private final WynkServiceDetailsCachingService wynkServiceDetailsCachingService;

    public static Map<String, String> map = Collections.singletonMap("airteltv", "airtelxstream");

    PaymentChargeConsumptionHandler(@Value("${wynk.kafka.producers.payment.charge.topic}") String topic,

                                    TaxUtils taxUtils,
                                    PaymentGatewayManager manager,
                                    IPaymentOptionServiceV2 payOptionService,
                                    PaymentCachingService paymentCachingService,
                                    PaymentGroupCachingService paymentGroupCachingService,
                                    PaymentMethodCachingService paymentMethodCachingService,
                                    IKafkaEventPublisher<String, WaPayChargeRespEvent> kafkaPublisher,
                                    WynkServiceDetailsCachingService wynkServiceDetailsCachingService) {
        this.topic = topic;
        this.taxUtils = taxUtils;
        this.manager = manager;
        this.kafkaPublisher = kafkaPublisher;
        this.payOptionService = payOptionService;
        this.paymentCachingService = paymentCachingService;
        this.paymentGroupCachingService = paymentGroupCachingService;
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.wynkServiceDetailsCachingService = wynkServiceDetailsCachingService;
    }

    //@Async
    @Override
    //@Retryable(maxAttempts = 4, backoff = @Backoff(delay = 100, multiplier = 2))
    public void charge(PaymentChargeRequestMessage requestMessage) {
        final PayChargeReqMessage request = requestMessage.getMessage();
        final WynkService service = wynkServiceDetailsCachingService.get(requestMessage.getServiceId());
        final String clientAlias = map.getOrDefault(requestMessage.getServiceId(), service.getLinkedClient());

        final List<Header> headers = new ArrayList() {{
            add(new RecordHeader(BaseConstants.ORG_ID, requestMessage.getOrgId().getBytes()));
            add(new RecordHeader(BaseConstants.SESSION_ID, requestMessage.getSessionId().getBytes()));
            add(new RecordHeader(BaseConstants.SERVICE_ID, requestMessage.getServiceId().getBytes()));
            add(new RecordHeader(BaseConstants.REQUEST_ID, requestMessage.getRequestId().getBytes()));
        }};

        try {
            final WhatsAppChargeRequest intentRequest = getData(clientAlias, request).sessionDetails(WhatsappSessionDetails.builder().sessionId(requestMessage.getSessionId()).orgId(requestMessage.getOrgId()).serviceId(requestMessage.getServiceId()).campaignId(request.getCampaignId()).requestId(requestMessage.getRequestId()).to(request.getTo()).from(request.getFrom()).build()).build();
            final UpiIntentChargingResponse intentResponse = (UpiIntentChargingResponse) manager.charge(intentRequest);
            final WaPayChargeRespEvent payChargeRespEvent = toPaymentChargeEvent(requestMessage, intentRequest, intentResponse);
            kafkaPublisher.publish(topic, null, System.currentTimeMillis(), null, payChargeRespEvent, headers);
        } catch (WynkRuntimeException e) {
            final WaPayChargeRespEvent<WaFailedOrderDetails> payChargeRespEvent =  toPaymentChargeEvent(e, requestMessage);
            kafkaPublisher.publish(topic, null, System.currentTimeMillis(), null, payChargeRespEvent, headers);
        } catch (Exception e) {
            final WaPayChargeRespEvent<WaFailedOrderDetails> payChargeRespEvent =  toPaymentChargeEvent(e, requestMessage);
            kafkaPublisher.publish(topic, null, System.currentTimeMillis(), null, payChargeRespEvent, headers);
            log.error("something went wrong ", e);
            throw new WynkRuntimeException(e);
        }
    }

    @ClientAware(clientAlias = "#clientAlias")
    private WhatsAppChargeRequest.WhatsAppChargeRequestBuilder getData(String clientAlias, PayChargeReqMessage request) {
        final FilteredPaymentOptionsResult payOptions = payOptionService.getPaymentOptions(DefaultPaymentOptionRequest.builder()
                .paymentOptionRequest(WhatsappPaymentOptionsRequest.builder()
                        .client(clientAlias)
                        .geoLocation(request.getGeoLocation())
                        .appDetails(request.getAppDetails())
                        .userDetails(request.getUserDetails())
                        .productDetails(request.getProductDetails())
                        .build())
                .build());

        final String paymentId = payOptions.getMethods().stream().filter(method -> method.getGroup().equalsIgnoreCase(UpiConstants.UPI)).sorted(Comparator.comparing(PaymentOptionsDTO.PaymentMethodDTO::getHierarchy)).findFirst().map(PaymentOptionsDTO.PaymentMethodDTO::getPaymentId).orElse(request.getPaymentDetails().getPaymentId());
        final UpiDetails upiDetails = new UpiDetails();
        upiDetails.setIntent(true);
        return WhatsAppChargeRequest.builder()
                .clientAlias(clientAlias)
                .paymentDetails(UpiPaymentDetails.builder()
                        .paymentMode(request.getPaymentDetails().getPaymentMode())
                        .trialOpted(request.getPaymentDetails().isTrialOpted())
                        .autoRenew(request.getPaymentDetails().isAutoRenew())
                        .mandate(request.isMandateSupported())
                        .upiDetails(upiDetails)
                        .paymentId(paymentId)
                        .build())
                .productDetails(request.getProductDetails())
                .geoLocation(request.getGeoLocation())
                .appDetails(request.getAppDetails())
                .userDetails(request.getUserDetails());
    }

    private WaPayChargeRespEvent<WaFailedOrderDetails> toPaymentChargeEvent(WynkRuntimeException ex, PaymentChargeRequestMessage requestMessage) {
        return WaPayChargeRespEvent.<WaFailedOrderDetails>builder()
                .sessionId(requestMessage.getSessionId())
                .to(requestMessage.getMessage().getFrom())
                .from(requestMessage.getMessage().getTo())
                .campaignId(requestMessage.getMessage().getCampaignId())
                .orderDetails(WaFailedOrderDetails.builder()
                        .status(TransactionStatus.FAILURE.getValue())
                        .event(PaymentEvent.PURCHASE.getValue())
                        .id(UUIDs.random().toString())
                        .errorMessage(ex.getMessage())
                        .errorCode(ex.getErrorCode())
                        .build())
                .build();
    }

    private WaPayChargeRespEvent<WaFailedOrderDetails> toPaymentChargeEvent(Exception ex, PaymentChargeRequestMessage requestMessage) {
        return WaPayChargeRespEvent.<WaFailedOrderDetails>builder()
                .sessionId(requestMessage.getSessionId())
                .to(requestMessage.getMessage().getFrom())
                .from(requestMessage.getMessage().getTo())
                .campaignId(requestMessage.getMessage().getCampaignId())
                .orderDetails(WaFailedOrderDetails.builder()
                        .status(TransactionStatus.FAILURE.getValue())
                        .event(PaymentEvent.PURCHASE.getValue())
                        .id(UUIDs.random().toString())
                        .errorMessage(ex.getMessage())
                        .errorCode("PAY001")
                        .build())
                .build();
    }

    private WaPayChargeRespEvent<WaOrderDetails> toPaymentChargeEvent(PaymentChargeRequestMessage requestMessage, WhatsAppChargeRequest chargeRequest, UpiIntentChargingResponse chargingResponse) {
        final Transaction transaction = TransactionContext.get();
        final PlanDTO selectedPlan = paymentCachingService.getPlan(transaction.getPlanId());
        final PaymentMethod method = paymentMethodCachingService.get(chargeRequest.getPaymentId());
        final PaymentGroup group = paymentGroupCachingService.get(method.getGroup());
        final String vpa = chargingResponse.getPa();
        if (group.getMeta().getOrDefault(vpa, BaseConstants.UNKNOWN).equals(BaseConstants.UNKNOWN)) {
            AnalyticService.update(PaymentConstants.UNKNOWN_VPA, vpa);
            log.error("Unknown vpa {} found for whatsApp", vpa);
        }
        final String prefix = (String) method.getMeta().getOrDefault(UPI_PREFIX, UPI.toLowerCase());
        return WaPayChargeRespEvent.<WaOrderDetails>builder()
                .sessionId(requestMessage.getSessionId())
                .to(requestMessage.getMessage().getFrom())
                .from(requestMessage.getMessage().getTo())
                .campaignId(requestMessage.getMessage().getCampaignId())
                .deeplink(chargingResponse.toDeeplink(requestMessage.getMessage().getPaymentDetails().isAutoRenew(), prefix))
                .orderDetails(WaOrderDetails.builder()
                        .taxDetails(TaxDetails.builder().value(taxUtils.calculateTax(transaction)).build())
                        .mandate(chargeRequest.getPaymentDetails().isMandate())
                        .code(chargeRequest.getPaymentDetails().getPaymentId())
                        .mandateAmount(transaction.getMandateAmount())
                        .status(transaction.getStatus().getValue())
                        .event(transaction.getType().getValue())
                        .pgCode(method.getPaymentCode().getId())
                        .discount(transaction.getDiscount())
                        .trial(chargeRequest.isTrialOpted())
                        .amount(transaction.getAmount())
                        .id(transaction.getIdStr())
                        .build())
                .planDetails(EligiblePlanDetails.builder().id(String.valueOf(selectedPlan.getId())).title(selectedPlan.getTitle()).description(selectedPlan.getDescription())
                        .priceDetails(PriceDetails.builder().currency(selectedPlan.getPrice().getCurrency()).price(selectedPlan.getPrice().getDisplayAmount().intValue()).discountPrice(selectedPlan.getPrice().getAmount()).build())
                        .periodDetails(PeriodDetails.builder().validity(selectedPlan.getPeriod().getValidity()).validityUnit(selectedPlan.getPeriod().getTimeUnit()).build())
                        .build())
                .payConfigId((String) group.getMeta().getOrDefault(vpa, BaseConstants.UNKNOWN))
                .build();
    }
}
