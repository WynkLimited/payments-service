package in.wynk.payment.gateway.aps;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentMethodDeleteResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.payu.ApsCallBackRequestPayload;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.gateway.aps.service.*;
import in.wynk.payment.service.*;
import in.wynk.payment.service.impl.ApsPaymentSettlementGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RequiredArgsConstructor
@Service(PaymentConstants.AIRTEL_PAY_STACK)
public class ApsGateway implements
        IPaymentOptionEligibility,
        IPreDebitNotificationService,
        IMerchantPaymentRenewalServiceV2<PaymentRenewalChargingMessage>,
        IVerificationService<AbstractVerificationResponse, VerificationRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload>,
        IMerchantPaymentRefundService<ApsPaymentRefundResponse, ApsPaymentRefundRequest>,
        IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest>,
        IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>,
        IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, PaymentGatewaySettlementRequest> {

    private final IPaymentOptionEligibility payOptionsGateway;
    private final ApsPreDebitNotificationGatewayServiceImpl preDebitGateway;
    private final IMerchantPaymentRenewalServiceV2<PaymentRenewalChargingMessage> renewalGateway;
    private final IVerificationService<AbstractVerificationResponse, VerificationRequest> verificationGateway;
    private final IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload> callbackGateway;
    private final IMerchantPaymentRefundService<ApsPaymentRefundResponse, ApsPaymentRefundRequest> refundGateway;
    private final IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest> deleteGateway;
    private final IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> statusGateway;
    private final IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> chargeGateway;
    private final IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, PaymentGatewaySettlementRequest> settlementGateway;

    public ApsGateway(@Value("${payment.merchant.aps.salt}") String salt,
                      @Value("${payment.merchant.aps.secret}") String secret,
                      @Value("${aps.payment.renewal.api}") String siPaymentApi,
                      @Value("${aps.payment.option.api}") String payOptionEndpoint,
                      @Value("${aps.payment.delete.vpa}") String deleteVpaEndpoint,
                      @Value("${aps.payment.predebit.api}") String preDebitEndpoint,
                      @Value("${aps.payment.init.refund.api}") String refundEndpoint,
                      @Value("${aps.payment.delete.card}") String deleteCardEndpoint,
                      @Value("${aps.payment.verify.vpa.api}") String vpaVerifyEndpoint,
                      @Value("${aps.payment.verify.bin.api}") String binVerifyEndpoint,
                      @Value("${aps.payment.init.charge.api}") String commonChargeEndpoint,
                      @Value("${aps.payment.init.charge.upi.api}") String upiChargeEndpoint,
                      @Value("${aps.payment.init.settlement.api}") String settlementEndpoint,
                      ObjectMapper mapper,
                      ApsCommonGatewayService commonGateway,
                      PaymentCachingService payCache,
                      PaymentMethodCachingService cache,
                      ApplicationEventPublisher eventPublisher,
                      ITransactionManagerService transactionManager,
                      IMerchantTransactionService merchantTransactionService,
                      @Qualifier("apsHttpTemplate") RestTemplate httpTemplate) {
        this.statusGateway = new ApsStatusGatewayServiceImpl(commonGateway);
        this.callbackGateway = new ApsCallbackGatewayServiceImpl(salt, secret, commonGateway, mapper);
        this.payOptionsGateway = new ApsPaymentOptionsGatewayServiceImpl(payOptionEndpoint, commonGateway);
        this.refundGateway = new ApsRefundGatewayServiceImpl(refundEndpoint, eventPublisher, commonGateway);
        this.deleteGateway = new ApsDeleteGatewayServiceImpl(deleteCardEndpoint, deleteVpaEndpoint, commonGateway);
        this.settlementGateway = new ApsPaymentSettlementGateway(settlementEndpoint, httpTemplate, payCache);
        this.chargeGateway = new ApsChargeGatewayServiceImpl(upiChargeEndpoint, commonChargeEndpoint, cache, commonGateway);
        this.preDebitGateway =  new ApsPreDebitNotificationGatewayServiceImpl(preDebitEndpoint, transactionManager, commonGateway);
        this.verificationGateway = new ApsVerificationGatewayServiceImpl(vpaVerifyEndpoint, binVerifyEndpoint, httpTemplate, commonGateway);
        this.renewalGateway = new ApsRenewalGatewayServiceImpl(siPaymentApi, commonGateway, merchantTransactionService, payCache, mapper, eventPublisher);
    }


    @Override
    public boolean isEligible(String msisdn, String payGroup, String payId) {
        return payOptionsGateway.isEligible(msisdn, payGroup, payId);
    }

    @Override
    public AbstractPaymentCallbackResponse handleCallback(ApsCallBackRequestPayload callbackRequest) {
        return callbackGateway.handleCallback(callbackRequest);
    }

    @Override
    public AbstractCoreChargingResponse charge(AbstractChargingRequestV2 request) {
        return chargeGateway.charge(request);
    }

    @Override
    public WynkResponseEntity<ApsPaymentRefundResponse> refund(ApsPaymentRefundRequest request) {
        return refundGateway.refund(request);
    }

    @Override
    public void renew(PaymentRenewalChargingMessage paymentRenewalChargingMessage) {
        renewalGateway.renew(paymentRenewalChargingMessage);
    }

    @Override
    public AbstractPaymentMethodDeleteResponse delete(PaymentMethodDeleteRequest request) {
        return deleteGateway.delete(request);
    }

    @Override
    public AbstractPaymentStatusResponse status(AbstractTransactionStatusRequest request) {
        return statusGateway.status(request);
    }

    @Override
    public AbstractPreDebitNotificationResponse notify(PreDebitNotificationMessage request) {
        return preDebitGateway.notify(request);
    }

    @Override
    public AbstractVerificationResponse verify(VerificationRequest request) {
        return verificationGateway.verify(request);
    }

    @Override
    public DefaultPaymentSettlementResponse settle(PaymentGatewaySettlementRequest request) {
        return settlementGateway.settle(request);
    }
}
