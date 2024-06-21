package in.wynk.payment.gateway.aps;

import static in.wynk.payment.dto.aps.common.ApsConstant.AIRTEL_PAY_STACK;
import static in.wynk.payment.dto.aps.common.ApsConstant.AIRTEL_PAY_STACK_V2;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import in.wynk.payment.dto.aps.request.callback.ApsOrderStatusCallBackPayload;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.AbstractRechargeOrderRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.AbstractVerificationRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.RechargeOrderRequest;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.AbstractRechargeOrderResponse;
import in.wynk.payment.dto.response.RechargeOrderResponse;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.gateway.*;
import in.wynk.payment.gateway.aps.service.*;
import in.wynk.payment.service.IExternalPaymentEligibilityService;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(AIRTEL_PAY_STACK_V2)
public class ApsOrderGateway implements IExternalPaymentEligibilityService, IPaymentInstrumentsProxy<PaymentOptionsEligibilityRequest>,
        IPaymentCallback<AbstractPaymentCallbackResponse, ApsCallBackRequestPayload>, IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>,
        IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>,
        IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest>,
        IPaymentRefund<ApsPaymentRefundResponse, ApsPaymentRefundRequest> {

    private final IRechargeOrder<AbstractRechargeOrderResponse, AbstractRechargeOrderRequest> orderGateway;
    private final IExternalPaymentEligibilityService eligibilityGateway;
    private final IPaymentRefund<ApsPaymentRefundResponse, ApsPaymentRefundRequest> refundGateway;
    private final IPaymentInstrumentsProxy<PaymentOptionsEligibilityRequest> payOptionsGateway;
    private final IMerchantTransactionService merchantTransactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> verificationGateway;
    private final ISubscriptionServiceManager subscriptionServiceManager;


    public ApsOrderGateway(@Value("${aps.payment.order.api}") String orderEndpoint,
                           @Value("${aps.payment.option.api}") String payOptionEndpoint,
                           @Value("${aps.payment.init.refund.api}") String refundEndpoint,
                           @Value("${aps.payment.verify.vpa.api}") String vpaVerifyEndpoint,
                           @Value("${aps.payment.verify.bin.api}") String binVerifyEndpoint,
                           @Qualifier("apsHttpTemplate") RestTemplate httpTemplate,
                           ApsCommonGatewayService commonGateway,
                           IMerchantTransactionService merchantTransactionService,
                           ApplicationEventPublisher eventPublisher,
                           final ISubscriptionServiceManager subscriptionServiceManager) {

        this.orderGateway = new ApsOrderGatewayServiceImpl(orderEndpoint, commonGateway);
        this.eligibilityGateway = new ApsEligibilityGatewayServiceImpl();
        this.verificationGateway = new ApsVerificationGatewayImpl(vpaVerifyEndpoint, binVerifyEndpoint, httpTemplate, commonGateway);
        this.payOptionsGateway = new ApsPaymentOptionsServiceImpl(payOptionEndpoint, commonGateway);
        this.refundGateway = new ApsRefundGatewayServiceImpl(refundEndpoint, eventPublisher, commonGateway);

        this.merchantTransactionService = merchantTransactionService;
        this.eventPublisher = eventPublisher;
        this.subscriptionServiceManager = subscriptionServiceManager;
    }

    @Override
    public AbstractPaymentChargingResponse charge(AbstractPaymentChargingRequest request) {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> subscriptionServiceManager.cacheAdditiveDays(request.getUserDetails().getMsisdn(), request.getProductDetails().getId()));

        RechargeOrderResponse orderResponse = (RechargeOrderResponse) orderGateway.order(RechargeOrderRequest.builder().build());
        request.setOrderId(orderResponse.getOrderId());
        final IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> chargingService =
            BeanLocatorFactory.getBean(AIRTEL_PAY_STACK,
                                       new ParameterizedTypeReference<IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>>() {
                                       });
        AbstractPaymentChargingResponse chargeResponse = chargingService.charge(request);
        publishMerchantTransactionEvent(orderResponse);
        return chargeResponse;
    }

    private void publishMerchantTransactionEvent(RechargeOrderResponse orderResponse) {
        Transaction transaction = TransactionContext.get();
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        mBuilder.orderId(orderResponse.getOrderId());
        eventPublisher.publishEvent(mBuilder.build());
    }

    @Override
    public boolean isEligible(PaymentMethod entity, PaymentOptionsPlanEligibilityRequest request) {
        return eligibilityGateway.isEligible(entity, request);
    }

    @Override
    public boolean isEligible(PaymentMethod entity, PaymentOptionsItemEligibilityRequest request) {
        return eligibilityGateway.isEligible(entity, request);
    }

    @Override
    public AbstractPaymentInstrumentsProxy<?, ?> load(PaymentOptionsEligibilityRequest request) {
        return payOptionsGateway.load(request);
    }

    @Override
    public AbstractPaymentCallbackResponse handle(ApsCallBackRequestPayload callbackRequest) {
        final IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest> callbackService =
            BeanLocatorFactory.getBean(AIRTEL_PAY_STACK, new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest>>() {
            });
        return callbackService.handle(callbackRequest);
    }

    @Override
    public ApsCallBackRequestPayload parse(Map<String, Object> payload) {
        final IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest> callbackService =
                BeanLocatorFactory.getBean(AIRTEL_PAY_STACK, new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, CallbackRequest>>() {
                });
        payload.put("lob", "PREPAID");
        ApsOrderStatusCallBackPayload response = (ApsOrderStatusCallBackPayload) callbackService.parse(payload);
        try {
            String txnId = merchantTransactionService.findTransactionId(response.getOrderId());
            response.setTxnId(txnId);
        } catch (Exception e) {
            log.error("Exception occurred while finding orderId in merchant table for order created with APS");
            throw new WynkRuntimeException(PaymentErrorType.APS010, e);
        }
        return response;
    }

    @Override
    public AbstractPaymentStatusResponse reconcile(AbstractTransactionStatusRequest request) {
        final IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> reconcileService =
            BeanLocatorFactory.getBean(AIRTEL_PAY_STACK, new ParameterizedTypeReference<IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>() {
            });
        return reconcileService.reconcile(request);
    }

    @Override
    public AbstractVerificationResponse verify(AbstractVerificationRequest request) {
        return verificationGateway.verify(request);
    }

    @Override
    public ApsPaymentRefundResponse doRefund(ApsPaymentRefundRequest request) {
        return refundGateway.doRefund(request);
    }
}
