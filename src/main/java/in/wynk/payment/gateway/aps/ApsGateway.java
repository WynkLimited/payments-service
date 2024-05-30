package in.wynk.payment.gateway.aps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.dto.aps.common.ApsConstant;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Slf4j
@Service(ApsConstant.AIRTEL_PAY_STACK)
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

    protected final IExternalPaymentEligibilityService eligibilityGateway;
    protected final IPaymentRenewal<PaymentRenewalChargingRequest> renewalGateway;
    protected final ICancellingRecurringService mandateCancellationGateway;
    protected final IPaymentRefund<ApsPaymentRefundResponse, ApsPaymentRefundRequest> refundGateway;
    protected final IPaymentInstrumentsProxy<PaymentOptionsEligibilityRequest> payOptionsGateway;
    protected final IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> callbackGateway;
    protected final IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> statusGateway;
    protected final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargeGateway;
    protected final IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> verificationGateway;
    protected final IPaymentSettlement<DefaultPaymentSettlementResponse, ApsGatewaySettlementRequest> settlementGateway;
    protected final IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest> deleteGateway;
    protected final IMerchantTDRService iMerchantTDRService;

    @Value("${payment.merchant.aps.salt}") String salt;
    @Value("${payment.merchant.aps.secret}") String secret;
    @Value("${aps.payment.renewal.api}") String siPaymentApi;
    @Value("${aps.payment.option.api}") String payOptionEndpoint;
    @Value("${aps.payment.delete.vpa}") String deleteVpaEndpoint;
    @Value("${aps.payment.init.refund.api}") String refundEndpoint;
    @Value("${aps.payment.delete.card}") String deleteCardEndpoint;
    @Value("${aps.payment.verify.vpa.api}") String vpaVerifyEndpoint;
    @Value("${aps.payment.verify.bin.api}") String binVerifyEndpoint;
    @Value("${aps.payment.init.charge.api}") String chargeEndpoint;
    @Value("${aps.payment.init.charge.paydigi.api}") String payDigiChargeEndpoint;
    @Value("${aps.payment.init.charge.upi.api}") String upiChargeEndpoint;
    @Value("${aps.payment.init.charge.upi.paydigi.api}") String upiPayDigiChargeEndpoint;
    @Value("${aps.payment.init.settlement.api}") String settlementEndpoint;
    @Value("${aps.payment.cancel.mandate.api}") String cancelMandateEndpoint;
    @Value("${aps.payment.tdr.api}") String tdrEndPoint;

    @Qualifier("apsHttpTemplate") RestTemplate httpTemplate;

    @Autowired
    protected Gson gson;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected ApsCommonGatewayService commonGateway;
    @Autowired
    protected PaymentCachingService paymentCachingService;
    @Autowired
    protected PaymentMethodCachingService paymentMethodCachingService;
    @Autowired
    protected ApplicationEventPublisher eventPublisher;
    @Autowired
    protected ITransactionManagerService transactionManager;
    @Autowired
    protected IMerchantTransactionService merchantTransactionService;
    @Autowired
    protected IRecurringPaymentManagerService recurringPaymentManagerService;
    @Autowired
    protected RecurringTransactionUtils recurringTransactionUtils;

    public ApsGateway() {
        this.eligibilityGateway = new ApsEligibilityGatewayServiceImpl();
        this.statusGateway = new ApsStatusGatewayServiceImpl(commonGateway);
        this.payOptionsGateway = new ApsPaymentOptionsServiceImpl(payOptionEndpoint, commonGateway);
        this.callbackGateway = new ApsCallbackGatewayServiceImpl(salt, secret, commonGateway, mapper, eventPublisher, recurringTransactionUtils);
        this.refundGateway = new ApsRefundGatewayServiceImpl(refundEndpoint, eventPublisher, commonGateway);
        this.settlementGateway = new ApsPaymentSettlementGateway(settlementEndpoint, httpTemplate, paymentCachingService);
        this.deleteGateway = new ApsDeleteGatewayServiceImpl(deleteCardEndpoint, deleteVpaEndpoint, commonGateway);
        this.chargeGateway = new ApsChargeGatewayServiceImpl(upiChargeEndpoint, chargeEndpoint, upiPayDigiChargeEndpoint, payDigiChargeEndpoint, paymentMethodCachingService, commonGateway);
        this.verificationGateway = new ApsVerificationGatewayImpl(vpaVerifyEndpoint, binVerifyEndpoint, httpTemplate, commonGateway);
        this.renewalGateway = new ApsRenewalGatewayServiceImpl(siPaymentApi, mapper, commonGateway, paymentCachingService, merchantTransactionService, eventPublisher, transactionManager, recurringPaymentManagerService);
        this.mandateCancellationGateway = new ApsCancelMandateGatewayServiceImpl(mapper, transactionManager, merchantTransactionService, cancelMandateEndpoint, commonGateway, gson);
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
    public void cancelRecurring (String transactionId) {
        mandateCancellationGateway.cancelRecurring(transactionId);
    }

    @Override
    public BaseTDRResponse getTDR (String transactionId) {
        return iMerchantTDRService.getTDR(transactionId);
    }
}
