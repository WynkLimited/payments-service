package in.wynk.payment.gateway.payu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.PreDebitRequest;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import in.wynk.payment.dto.payu.PayUPaymentRefundResponse;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.AbstractVerificationRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.gateway.*;
import in.wynk.payment.gateway.payu.service.*;
import in.wynk.payment.service.*;
import in.wynk.payment.service.impl.PayUMerchantPaymentService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static in.wynk.payment.core.constant.BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;

/**
 * @author Nishesh Pandey
 */
@Service(PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUGateway extends PayUMerchantPaymentService implements
        IPaymentRenewal<PaymentRenewalChargingRequest>,
        IPaymentRefund<PayUPaymentRefundResponse, PayUPaymentRefundRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload>,
        IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest>,
        IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>, IMerchantTDRService, IPreDebitNotificationService {

    private final PayURefundGatewayImpl refundGateway;
    private final PayUStatusGatewayImpl statusGateway;
    private final PayUChargingGatewayImpl chargeGateway;
    private final PayURenewalGatewayImpl renewalGateway;
    private final PayUCallbackGatewayImpl callbackGateway;
    private final PayUVerificationGatewayImpl verificationGateway;
    private final PayUPreDebitGatewayServiceImpl preDebitGatewayService;
    private final IMerchantTDRService iMerchantTDRService;


    public PayUGateway(@Value("${payment.merchant.payu.api.payment}") String paymentApi,
                       @Value("${payment.merchant.payu.api.info}") String payuInfoApi,
                       Gson gson,
                       ObjectMapper mapper,
                       PaymentCachingService payCache,
                       PayUCommonGateway commonGateway,
                       PaymentMethodCachingService cache,
                       IErrorCodesCacheService errorCodeCache,
                       ApplicationEventPublisher eventPublisher,
                       ITransactionManagerService transactionManagerService,
                       IMerchantTransactionService merchantTransactionService,
                       IRecurringPaymentManagerService recurringPaymentManagerService,
                       RecurringTransactionUtils recurringTransactionUtils,
                       @Qualifier(EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        super(gson, mapper, eventPublisher, payCache, merchantTransactionService, errorCodeCache, restTemplate, transactionManagerService, recurringPaymentManagerService, recurringTransactionUtils);
        this.statusGateway = new PayUStatusGatewayImpl(commonGateway);
        this.callbackGateway = new PayUCallbackGatewayImpl(commonGateway, mapper, eventPublisher);
        this.refundGateway = new PayURefundGatewayImpl(commonGateway, eventPublisher, transactionManagerService);
        this.verificationGateway = new PayUVerificationGatewayImpl(commonGateway, mapper);
        this.chargeGateway = new PayUChargingGatewayImpl(commonGateway, cache, paymentApi);
        this.renewalGateway = new PayURenewalGatewayImpl(commonGateway, gson, mapper, payCache, eventPublisher, transactionManagerService, recurringPaymentManagerService, recurringTransactionUtils);
        this.iMerchantTDRService = new PayUTdrGatewayServiceImpl(payuInfoApi, commonGateway, merchantTransactionService);
        this.preDebitGatewayService = new PayUPreDebitGatewayServiceImpl(gson,mapper, payCache, commonGateway, recurringPaymentManagerService, recurringTransactionUtils);
    }

    @Override
    public PayUCallbackRequestPayload parse(Map<String, Object> payload) {
        return callbackGateway.parse(payload);
    }

    @Override
    public AbstractPaymentCallbackResponse handle(PayUCallbackRequestPayload callbackRequest) {
        return callbackGateway.handle(callbackRequest);
    }

    @Override
    public void renew(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        renewalGateway.renew(paymentRenewalChargingRequest);
    }

    @Override
    public AbstractPaymentChargingResponse charge(AbstractPaymentChargingRequest request) {
        return chargeGateway.charge(request);
    }

    @Override
    public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
        return statusGateway.reconcile(request);
    }

    @Override
    public AbstractVerificationResponse verify(AbstractVerificationRequest verificationRequest) {
        return verificationGateway.verify(verificationRequest);
    }

    @Override
    public PayUPaymentRefundResponse doRefund(PayUPaymentRefundRequest request) {
        return refundGateway.doRefund(request);
    }

    @Override
    public BaseTDRResponse getTDR (String transactionId) {
        return iMerchantTDRService.getTDR(transactionId);
    }

    @Override
    public AbstractPreDebitNotificationResponse notify (PreDebitRequest request) {
        return preDebitGatewayService.notify(request);
    }
}
