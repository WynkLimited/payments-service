package in.wynk.payment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.core.constant.ClientLoggingMarker;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.ChecksumUtils;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.event.*;
import in.wynk.payment.dto.request.ClientCallbackRequest;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IPaymentErrorService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.constant.QueueConstant;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.EnumSet;
import java.util.Optional;

import static in.wynk.queue.constant.BeanConstant.MESSAGE_PAYLOAD;

@Service
@Slf4j
public class PaymentEventListener {

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final RetryRegistry retryRegistry;
    private final PaymentManager paymentManager;
    private final IPaymentErrorService paymentErrorService;
    private final IMerchantTransactionService merchantTransactionService;
    private final ClientDetailsCachingService clientDetailsCachingService;

    public PaymentEventListener(ObjectMapper mapper, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_CLIENT_S2S_TEMPLATE) RestTemplate restTemplate, RetryRegistry retryRegistry, PaymentManager paymentManager, IPaymentErrorService paymentErrorService, IMerchantTransactionService merchantTransactionService, ClientDetailsCachingService clientDetailsCachingService) {
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.retryRegistry = retryRegistry;
        this.paymentManager = paymentManager;
        this.paymentErrorService = paymentErrorService;
        this.merchantTransactionService = merchantTransactionService;
        this.clientDetailsCachingService = clientDetailsCachingService;
    }

    @EventListener
    @AnalyseTransaction(name = QueueConstant.DEFAULT_SQS_MESSAGE_THRESHOLD_EXCEED_EVENT)
    public void onAnyOrderMessageThresholdExceedEvent(MessageThresholdExceedEvent event) throws JsonProcessingException {
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
        paymentManager.initRefund(event.getOriginalTransactionId());
    }

    @EventListener
    @AnalyseTransaction(name = "paymentRefundEvent")
    public void onPaymentRefundEvent(PaymentRefundedEvent event) {
        AnalyticService.update(event);
    }

    @EventListener
    @AnalyseTransaction(name = "paymentReconciledEvent")
    public void onPaymentReconciledEvent(PaymentReconciledEvent event) {
        AnalyticService.update(event);
        Optional<Client> clientOptional = Optional.ofNullable(clientDetailsCachingService.getClientByAlias(event.getClientAlias()));
        if (clientOptional.isPresent()) {
            Client client = clientOptional.get();
            Optional<Boolean> callbackOptional = client.getMeta(BaseConstants.CALLBACK_ENABLED);
            if (callbackOptional.isPresent() && callbackOptional.get()) {
                Optional<String> callbackUrlOptional = client.getMeta(BaseConstants.CALLBACK_URL);
                if (callbackUrlOptional.isPresent() && !EnumSet.of(PaymentEvent.REFUND).contains(event.getPaymentEvent())) {
                    ClientCallbackRequest clientCallbackRequest = ClientCallbackRequest.builder()
                            .uid(event.getUid())
                            .msisdn(event.getMsisdn())
                            .itemId(event.getItemId())
                            .planId(event.getPlanId())
                            .transactionId(event.getTransactionId())
                            .transactionStatus(event.getTransactionStatus().getValue())
                            .build();
                    RequestEntity<ClientCallbackRequest> requestHttpEntity = ChecksumUtils.buildEntityWithChecksum(callbackUrlOptional.get(), client.getClientId(), client.getClientSecret(), clientCallbackRequest, HttpMethod.POST);
                    AnalyticService.update(BaseConstants.CLIENT_REQUEST, requestHttpEntity.toString());
                    try {
                        ResponseEntity<String> partnerResponse = restTemplate.exchange(requestHttpEntity, String.class);
                        AnalyticService.update(BaseConstants.CLIENT_RESPONSE, partnerResponse.toString());
                    } catch (HttpStatusCodeException exception) {
                        AnalyticService.update(BaseConstants.CLIENT_RESPONSE, exception.getResponseBodyAsString());
                    } catch (Exception exception) {
                        log.error(ClientLoggingMarker.CLIENT_COMMUNICATION_ERROR, exception.getMessage() + " for client " + event.getClientAlias(), exception);
                    }
                } else {
                    log.warn(ClientLoggingMarker.INVALID_CLIENT_DETAILS, "Callback url is not provided despite callback is enabled for client {}", event.getClientAlias());
                }
            }
        }
    }

}
