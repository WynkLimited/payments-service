package in.wynk.payment.gateway.payu;

import com.google.gson.Gson;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentValidationResponse;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayUChargingRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.gateway.IPaymentCallback;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.gateway.IPaymentInstrumentValidator;
import in.wynk.payment.gateway.payu.callback.PayUCallbackGateway;
import in.wynk.payment.gateway.payu.charge.PayUChargingGateway;
import in.wynk.payment.gateway.payu.common.PayUCommonGateway;
import in.wynk.payment.gateway.payu.verify.PayUVerificationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.VERSION_2;

@Slf4j
@Service(PAYU_MERCHANT_PAYMENT_SERVICE + VERSION_2)
public class PayUPayment implements
        IPaymentCharging<AbstractChargingGatewayResponse, PayUChargingRequest<?>>,
        IPaymentInstrumentValidator<AbstractPaymentInstrumentValidationResponse, VerificationRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> {

    private final IPaymentCharging<AbstractChargingGatewayResponse, PayUChargingRequest<?>> chargeGateway;
    private final IPaymentInstrumentValidator<AbstractPaymentInstrumentValidationResponse, VerificationRequest> verifyGateway;
    private final IPaymentCallback<AbstractPaymentCallbackResponse, PayUCallbackRequestPayload> callbackGateway;

    @Value("${payment.merchant.payu.api.info}")
    private String INFO_API;
    @Value("${payment.merchant.payu.api.payment}")
    private String PAYMENT_API;
    @Value("${payment.encKey}")
    private String encryptionKey;
    @Autowired
    private Gson gson;

    public PayUPayment(PayUCommonGateway common) {
        this.chargeGateway = new PayUChargingGateway(common);
        this.verifyGateway = new PayUVerificationGateway(common);
        this.callbackGateway = new PayUCallbackGateway(gson, common);
    }

    @Override
    public AbstractChargingGatewayResponse charge(PayUChargingRequest<?> request) {
        return chargeGateway.charge(request);
    }

    @Override
    public AbstractPaymentInstrumentValidationResponse verify(VerificationRequest request) {
        return verifyGateway.verify(request);
    }

    @Override
    public AbstractPaymentCallbackResponse handleCallback(PayUCallbackRequestPayload request) {
        return callbackGateway.handleCallback(request);
    }
}