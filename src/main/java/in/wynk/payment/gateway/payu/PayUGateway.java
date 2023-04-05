package in.wynk.payment.gateway.payu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.gateway.IPaymentRenewal;
import in.wynk.payment.gateway.payu.service.PayUCallbackGatewayService;
import in.wynk.payment.gateway.payu.service.PayUChargingGatewayService;
import in.wynk.payment.gateway.payu.service.PayUCommonGatewayService;
import in.wynk.payment.gateway.payu.service.PayURefundGatewayService;
import in.wynk.payment.gateway.payu.service.PayURenewalGatewayService;
import in.wynk.payment.gateway.payu.service.PayUStatusGatewayService;
import in.wynk.payment.gateway.payu.service.PayUVerificationGatewayService;
import in.wynk.payment.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Service(PaymentConstants.PAYU)
public class PayUGateway implements IVerificationService<AbstractVerificationResponse, VerificationRequest>,
        IPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload>, IPaymentRenewal<PaymentRenewalChargingRequest> {

    private final PayUVerificationGatewayService verificationGateway;
    private final PayUChargingGatewayService chargeGateway;
    private final PayUStatusGatewayService statusGateway;
    private final PayURenewalGatewayService renewalGateway;
    private final PayURefundGatewayService refundGateway;
    private final PayUCallbackGatewayService callbackGateway;

    public PayUGateway(  @Value("${payment.merchant.payu.api.payment}") String paymentApi,
                      ObjectMapper mapper,
                       Gson gson,
                      PayUCommonGatewayService commonGateway,
                      PaymentCachingService payCache,
                      PaymentMethodCachingService cache,
                      ApplicationEventPublisher eventPublisher,
                      IMerchantTransactionService merchantTransactionService) {
        this.statusGateway = new PayUStatusGatewayService(commonGateway);
        this.callbackGateway = new PayUCallbackGatewayService(commonGateway, eventPublisher, mapper);
        this.refundGateway = new PayURefundGatewayService(eventPublisher, commonGateway);
        this.chargeGateway = new PayUChargingGatewayService(commonGateway, cache, paymentApi);
        this.verificationGateway = new PayUVerificationGatewayService( commonGateway, mapper);
        this.renewalGateway = new PayURenewalGatewayService(commonGateway,gson, mapper, payCache, eventPublisher, merchantTransactionService);
    }


    @Override
    public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload callbackRequest) {
        return callbackGateway.handleCallback(callbackRequest);
    }

    @Override
    public void doRenewal (PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        renewalGateway.doRenewal(paymentRenewalChargingRequest);
    }

    @Override
    public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
        return chargeGateway.charge(request);
    }

    @Override
    public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
        return statusGateway.status(request);
    }

    @Override
    public AbstractVerificationResponse verify (VerificationRequest verificationRequest) {
        return verificationGateway.verify(verificationRequest);
    }
}
