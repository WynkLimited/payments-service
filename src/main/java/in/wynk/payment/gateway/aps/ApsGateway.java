package in.wynk.payment.gateway.aps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.BaseTDRResponse;
import static in.wynk.payment.core.constant.BeanConstant.AIRTEL_PAY_STACK;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.common.response.AbstractPaymentAccountDeletionResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.gateway.*;
import in.wynk.payment.gateway.aps.service.*;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.stream.service.IDataPlatformKafkaService;
import in.wynk.subscription.common.message.CancelMandateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Slf4j
@Service(AIRTEL_PAY_STACK)
public class ApsGateway implements
        IExternalPaymentEligibilityService,
        IPaymentRenewal<PaymentRenewalChargingRequest>,
        IPaymentInstrumentsProxy<PaymentOptionsEligibilityRequest>,
        IPaymentRefund<ApsPaymentRefundResponse, ApsPaymentRefundRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload>,
        IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest>,
        IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>,
        IPaymentSettlement<DefaultPaymentSettlementResponse, ApsGatewaySettlementRequest>,
        IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest>,
        ICancellingRecurringService, IMerchantTDRService {

    private final IExternalPaymentEligibilityService eligibilityGateway;
    private final IPaymentRenewal<PaymentRenewalChargingRequest> renewalGateway;
    private final ICancellingRecurringService mandateCancellationGateway;
    private final IPaymentRefund<ApsPaymentRefundResponse, ApsPaymentRefundRequest> refundGateway;
    private final IPaymentInstrumentsProxy<PaymentOptionsEligibilityRequest> payOptionsGateway;
    private final IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> callbackGateway;
    private final IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> statusGateway;
    private final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargeGateway;
    private final IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService;
    private final IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> verificationGateway;
    private final IPaymentSettlement<DefaultPaymentSettlementResponse, ApsGatewaySettlementRequest> settlementGateway;
    private final IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest> deleteGateway;
    private final IMerchantTDRService iMerchantTDRService;

    public ApsGateway(@Value("${payment.merchant.aps.salt}") String salt,
                      @Value("${payment.merchant.aps.secret}") String secret,
                      @Value("${aps.payment.renewal.api}") String siPaymentApi,
                      @Value("${aps.payment.option.api}") String payOptionEndpoint,
                      @Value("${aps.payment.delete.vpa}") String deleteVpaEndpoint,
                      @Value("${aps.payment.init.refund.api}") String refundEndpoint,
                      IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService, @Value("${aps.payment.delete.card}") String deleteCardEndpoint,
                      @Value("${aps.payment.verify.vpa.api}") String vpaVerifyEndpoint,
                      @Value("${aps.payment.verify.bin.api}") String binVerifyEndpoint,
                      @Value("${aps.payment.init.charge.api}") String chargeEndpoint,
                      @Value("${aps.payment.init.charge.paydigi.api}") String payDigiChargeEndpoint,
                      @Value("${aps.payment.init.charge.upi.api}") String upiChargeEndpoint,
                      @Value("${aps.payment.init.charge.upi.paydigi.api}") String upiPayDigiChargeEndpoint,
                      @Value("${aps.payment.init.settlement.api}") String settlementEndpoint,
                      @Value("${aps.payment.cancel.mandate.api}") String cancelMandateEndpoint,
                      @Value("${aps.payment.tdr.api}") String tdrEndPoint,
                      Gson gson,
                      ObjectMapper mapper,
                      ApsCommonGatewayService commonGateway,
                      PaymentCachingService paymentCachingService,
                      PaymentMethodCachingService paymentMethodCachingService,
                      ApplicationEventPublisher eventPublisher,
                      ITransactionManagerService transactionManager,
                      IMerchantTransactionService merchantTransactionService,
                      IRecurringPaymentManagerService recurringPaymentManagerService,
                      RecurringTransactionUtils recurringTransactionUtils,
                      @Qualifier("apsHttpTemplate") RestTemplate httpTemplate,
                      IDataPlatformKafkaService dataPlatformKafkaService) {
        this.kafkaPublisherService = kafkaPublisherService;
        this.eligibilityGateway = new ApsEligibilityGatewayServiceImpl();
        this.statusGateway = new ApsStatusGatewayServiceImpl(commonGateway, dataPlatformKafkaService);
        this.payOptionsGateway = new ApsPaymentOptionsServiceImpl(payOptionEndpoint, commonGateway);
        this.callbackGateway = new ApsCallbackGatewayServiceImpl(salt, secret, commonGateway, mapper, eventPublisher, recurringTransactionUtils, dataPlatformKafkaService);
        this.refundGateway = new ApsRefundGatewayServiceImpl(refundEndpoint, eventPublisher, commonGateway);
        this.settlementGateway = new ApsPaymentSettlementGateway(settlementEndpoint, httpTemplate, paymentCachingService);
        this.deleteGateway = new ApsDeleteGatewayServiceImpl(deleteCardEndpoint, deleteVpaEndpoint, commonGateway);
        this.chargeGateway = new ApsChargeGatewayServiceImpl(upiChargeEndpoint, chargeEndpoint, upiPayDigiChargeEndpoint, payDigiChargeEndpoint, paymentMethodCachingService, commonGateway, dataPlatformKafkaService);
        this.verificationGateway = new ApsVerificationGatewayImpl(vpaVerifyEndpoint, binVerifyEndpoint, httpTemplate, commonGateway);
        this.renewalGateway = new ApsRenewalGatewayServiceImpl(siPaymentApi, mapper, commonGateway, paymentCachingService, merchantTransactionService, eventPublisher, transactionManager, recurringPaymentManagerService);
        this.mandateCancellationGateway = new ApsCancelMandateGatewayServiceImpl(mapper, transactionManager, merchantTransactionService, cancelMandateEndpoint, commonGateway, gson, kafkaPublisherService, dataPlatformKafkaService);
        this.iMerchantTDRService = new ApsTdrGatewayServiceImpl(tdrEndPoint, httpTemplate, commonGateway, merchantTransactionService);
    }

    @Override
    public ApsCallBackRequestPayload parse(Map<String, Object> payload) {
        return callbackGateway.parse(payload);
    }

    @Override
    public AbstractPaymentCallbackResponse handle(ApsCallBackRequestPayload callbackRequest) {
        return callbackGateway.handle(callbackRequest);
    }

    @Override
    public AbstractPaymentChargingResponse charge(AbstractPaymentChargingRequest request) {
        return chargeGateway.charge(request);
    }

    @Override
    public ApsPaymentRefundResponse doRefund(ApsPaymentRefundRequest request) {
        return refundGateway.doRefund(request);
    }

    @Override
    public void renew(PaymentRenewalChargingRequest request) {
        renewalGateway.renew(request);
    }

    @Override
    @CacheEvict(cacheName = "APS_ELIGIBILITY_API", cacheKey = "#request.getMsisdn()", cacheManager = L2CACHE_MANAGER)
    public AbstractPaymentAccountDeletionResponse delete(AbstractPaymentAccountDeletionRequest request) {
        return deleteGateway.delete(request);
    }

    @Override
    public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
        return statusGateway.reconcile(request);
    }

    @Override
    public AbstractVerificationResponse verify(AbstractVerificationRequest request) {
        return verificationGateway.verify(request);
    }

    @Override
    public DefaultPaymentSettlementResponse settle(ApsGatewaySettlementRequest request) {
        return settlementGateway.settle(request);
    }

    @Override
    public AbstractPaymentInstrumentsProxy<?, ?> load (PaymentOptionsEligibilityRequest request) {

        return payOptionsGateway.load(request);
    }

    @Override
    public boolean isEligible(PaymentMethod entity, PaymentOptionsPlanEligibilityRequest request) {
        return eligibilityGateway.isEligible(entity, request);
    }

    @Override
    public boolean isEligible (PaymentMethod entity, PaymentOptionsItemEligibilityRequest request) {
        return  eligibilityGateway.isEligible(entity, request);
    }

    @Override
    public void cancelRecurring (String transactionId, PaymentEvent paymentEvent) {
        mandateCancellationGateway.cancelRecurring(transactionId, paymentEvent);
    }

    @Override
    public BaseTDRResponse getTDR (String transactionId) {
        return iMerchantTDRService.getTDR(transactionId);
    }
}
