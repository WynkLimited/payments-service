package in.wynk.payment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.event.*;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.request.ClientCallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IPaymentErrorService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.constant.QueueConstant;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

import static in.wynk.queue.constant.BeanConstant.MESSAGE_PAYLOAD;

@Slf4j
@Service
public class PaymentEventListener {

    private final ObjectMapper mapper;
    private final RetryRegistry retryRegistry;
    private final PaymentManager paymentManager;
    private final IPaymentErrorService paymentErrorService;
    private final ApplicationEventPublisher eventPublisher;
    private final IMerchantTransactionService merchantTransactionService;

    public PaymentEventListener(ObjectMapper mapper, RetryRegistry retryRegistry, PaymentManager paymentManager, IPaymentErrorService paymentErrorService, ApplicationEventPublisher eventPublisher, IMerchantTransactionService merchantTransactionService) {
        this.mapper = mapper;
        this.retryRegistry = retryRegistry;
        this.paymentManager = paymentManager;
        this.paymentErrorService = paymentErrorService;
        this.eventPublisher = eventPublisher;
        this.merchantTransactionService = merchantTransactionService;
    }

    @EventListener
    @AnalyseTransaction(name = QueueConstant.DEFAULT_SQS_MESSAGE_THRESHOLD_EXCEED_EVENT)
    public void onAnyOrderMessageThresholdExceedEvent(MessageThresholdExceedEvent event) throws
            JsonProcessingException {
        AnalyticService.update(event);
        AnalyticService.update(MESSAGE_PAYLOAD, mapper.writeValueAsString(event));
    }

    @EventListener
    @AnalyseTransaction(name = "recurringPaymentEvent")
    public void onRecurringPaymentEvent(RecurringPaymentEvent event) { // for auditing and stop recurring in external payment gateway
    }

    @EventListener
    @AnalyseTransaction(name = "merchantTransactionEvent")
    public void onMerchantTransactionEvent(MerchantTransactionEvent event) {
        AnalyticService.update(event);
        retryRegistry.retry(PaymentConstants.MERCHANT_TRANSACTION_UPSERT_RETRY_KEY).executeRunnable(() -> merchantTransactionService.upsert(MerchantTransaction.builder()
                .id(event.getId())
                .externalTransactionId(event.getExternalTransactionId())
                .request(event.getRequest())
                .response(event.getResponse())
                .build()
        ));
    }

    @EventListener
    @AnalyseTransaction(name = "paymentErrorEvent")
    public void onPaymentErrorEvent(PaymentErrorEvent event) {
        AnalyticService.update(event);
        retryRegistry.retry(PaymentConstants.PAYMENT_ERROR_UPSERT_RETRY_KEY).executeRunnable(() -> paymentErrorService.upsert(PaymentError.builder()
                .id(event.getId())
                .code(event.getCode())
                .description(event.getDescription())
                .build()));
    }

    @EventListener
    @AnalyseTransaction(name = "paymentRefundInitEvent")
    public void onPaymentRefundInitEvent(PaymentRefundInitEvent event) {
        AnalyticService.update(event);
        BaseResponse<?> response = paymentManager.initRefund(PaymentRefundInitRequest.builder().originalTransactionId(event.getOriginalTransactionId()).reason(event.getReason()).build());
        AnalyticService.update(response.getBody());
    }

    @EventListener
    @AnalyseTransaction(name = "paymentRefundedEvent")
    public void onPaymentRefundEvent(PaymentRefundReconciledEvent event) {
        AnalyticService.update(event);
    }

    @EventListener
    @AnalyseTransaction(name = "paymentReconciledEvent")
    public void onPaymentReconciledEvent(PaymentChargingReconciledEvent event) {
        AnalyticService.update(event);
        initRefundIfApplicable(event);
    }

    @EventListener
    @AnalyseTransaction(name = "clientCallback")
    public void onClientCallbackEvent(ClientCallbackEvent callbackEvent) {
        AnalyticService.update(callbackEvent);
        paymentManager.sendClientCallback(callbackEvent.getClientAlias(), ClientCallbackRequest.from(callbackEvent));
    }

    private void initRefundIfApplicable(PaymentChargingReconciledEvent event) {
        if (EnumSet.of(TransactionStatus.SUCCESS).contains(event.getTransactionStatus()) && EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION).contains(event.getPaymentEvent()) && event.getPaymentCode().isTrialRefundSupported()) {
            eventPublisher.publishEvent(PaymentRefundInitEvent.builder()
                    .reason("trial plan amount refund")
                    .originalTransactionId(event.getTransactionId())
                    .build());
        }
    }
}
