package in.wynk.payment.gateway.payu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import in.wynk.payment.dto.payu.PayUPaymentRefundResponse;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.gateway.IPaymentRenewal;
import in.wynk.payment.gateway.payu.service.PayUCallbackGatewayService;
import in.wynk.payment.gateway.payu.service.PayUChargingGatewayServiceImpl;
import in.wynk.payment.gateway.payu.service.PayUCommonGatewayService;
import in.wynk.payment.gateway.payu.service.PayURefundGatewayServiceImpl;
import in.wynk.payment.gateway.payu.service.PayURenewalGatewayServiceImpl;
import in.wynk.payment.gateway.payu.service.PayUStatusGatewayServiceImpl;
import in.wynk.payment.gateway.payu.service.PayUVerificationGatewayServiceImpl;
import in.wynk.payment.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Service(PaymentConstants.PAYU)
public class PayUGateway implements
        IPaymentRenewal<PaymentRenewalChargingRequest>,
        IVerificationService<AbstractVerificationResponse, VerificationRequest>,
        IPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>,
        IMerchantPaymentRefundService<PayUPaymentRefundResponse, PayUPaymentRefundRequest>,
        IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

    private final PayURefundGatewayServiceImpl refundGateway;
    private final PayUStatusGatewayServiceImpl statusGateway;
    private final PayUChargingGatewayServiceImpl chargeGateway;
    private final PayURenewalGatewayServiceImpl renewalGateway;
    private final PayUCallbackGatewayService callbackGateway;
    private final PayUVerificationGatewayServiceImpl verificationGateway;


    public PayUGateway(@Value("${payment.merchant.payu.api.payment}") String paymentApi,
                       Gson gson,
                       ObjectMapper mapper,
                       PaymentCachingService payCache,
                       PaymentMethodCachingService cache,
                       PayUCommonGatewayService commonGateway,
                       ApplicationEventPublisher eventPublisher,
                       IMerchantTransactionService merchantTransactionService) {
        this.statusGateway = new PayUStatusGatewayServiceImpl(commonGateway);
        this.callbackGateway = new PayUCallbackGatewayService(commonGateway, mapper);
        this.refundGateway = new PayURefundGatewayServiceImpl(commonGateway, eventPublisher);
        this.chargeGateway = new PayUChargingGatewayServiceImpl(commonGateway, cache, paymentApi);
        this.verificationGateway = new PayUVerificationGatewayServiceImpl(commonGateway, mapper);
        this.renewalGateway = new PayURenewalGatewayServiceImpl(commonGateway, gson, mapper, payCache, eventPublisher, merchantTransactionService);
    }


    @Override
    public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload callbackRequest) {
        return callbackGateway.handleCallback(callbackRequest);
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        renewalGateway.doRenewal(paymentRenewalChargingRequest);
    }

    @Override
    public AbstractCoreChargingResponse charge(AbstractChargingRequestV2 request) {
        return chargeGateway.charge(request);
    }

    @Override
    public AbstractPaymentStatusResponse status(AbstractTransactionStatusRequest request) {
        return statusGateway.status(request);
    }

    @Override
    public AbstractVerificationResponse verify(VerificationRequest verificationRequest) {
        return verificationGateway.verify(verificationRequest);
    }

    @Override
    public WynkResponseEntity<PayUPaymentRefundResponse> refund(PayUPaymentRefundRequest request) {
        return refundGateway.refund(request);
    }
}
