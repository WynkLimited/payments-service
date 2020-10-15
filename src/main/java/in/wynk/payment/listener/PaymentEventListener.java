package in.wynk.payment.listener;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.auth.service.IClientDetailsService;
import in.wynk.client.core.constant.ClientLoggingMarker;
import in.wynk.commons.constants.BaseConstants;
import in.wynk.commons.utils.ChecksumUtils;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.dto.request.ClientCallbackRequest;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IPaymentErrorService;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
@Slf4j
public class PaymentEventListener {

    private final RestTemplate restTemplate;
    private final RetryRegistry retryRegistry;
    private final IPaymentErrorService paymentErrorService;
    private final IClientDetailsService<Client> clientDetailsService;
    private final IMerchantTransactionService merchantTransactionService;

    public PaymentEventListener(@Qualifier(BeanConstant.EXTERNAL_PAYMENT_CLIENT_S2S_TEMPLATE) RestTemplate restTemplate, RetryRegistry retryRegistry, IPaymentErrorService paymentErrorService, IClientDetailsService<Client> clientDetailsService, IMerchantTransactionService merchantTransactionService) {
        this.restTemplate = restTemplate;
        this.retryRegistry = retryRegistry;
        this.paymentErrorService = paymentErrorService;
        this.clientDetailsService = clientDetailsService;
        this.merchantTransactionService = merchantTransactionService;
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
    @AnalyseTransaction(name = "paymentReconciledEvent")
    public void onPaymentReconciledEvent(PaymentReconciledEvent event) {
        AnalyticService.update(event);
        Optional<Client> clientOptional = clientDetailsService.getClientDetails(event.getClientId());
        if (clientOptional.isPresent()) {
            Client client = clientOptional.get();
            Optional<Boolean> callbackOptional = client.getMeta(BaseConstants.CALLBACK_ENABLED);
            if (callbackOptional.isPresent() && callbackOptional.get()) {
                Optional<String> callbackUrlOptional = client.getMeta(BaseConstants.CALLBACK_URL);
                if (callbackUrlOptional.isPresent()) {
                    ClientCallbackRequest clientCallbackRequest = ClientCallbackRequest.builder()
                            .uid(event.getUid())
                            .msisdn(event.getMsisdn())
                            .itemId(event.getItemId())
                            .planId(event.getPlanId())
                            .transactionId(event.getTransactionId())
                            .transactionStatus(event.getTransactionEvent())
                            .build();
                    HttpEntity<ClientCallbackRequest> requestHttpEntity = buildEntityForClient(callbackUrlOptional.get(), client.getClientId(), client.getClientSecret(), clientCallbackRequest);
                    AnalyticService.update(BaseConstants.CLIENT_REQUEST, requestHttpEntity.toString());
                    try {
                        ResponseEntity<String> partnerResponse = restTemplate.exchange(callbackUrlOptional.get(), HttpMethod.POST, requestHttpEntity, String.class);
                        AnalyticService.update(BaseConstants.CLIENT_RESPONSE, partnerResponse.toString());
                    } catch (HttpStatusCodeException exception) {
                        AnalyticService.update(BaseConstants.CLIENT_RESPONSE, exception.getResponseBodyAsString());
                    } catch (Exception exception) {
                        log.error(ClientLoggingMarker.CLIENT_COMMUNICATION_ERROR, exception.getMessage() + " for client " + event.getClientId(), exception);
                    }
                } else {
                    log.warn(ClientLoggingMarker.INVALID_CLIENT_DETAILS, "Callback url is not provided despite callback is enabled for client {}", event.getClientId());
                }
            }
        }
    }

    private <T> HttpEntity<T> buildEntityForClient(String endpoint, String clientId, String clientSecret, T response) {
        String checksum = ChecksumUtils.generate(clientId, clientSecret, endpoint, HttpMethod.POST, response);
        HttpHeaders headers = new HttpHeaders();
        headers.add(BaseConstants.PARTNER_X_CHECKSUM_TOKEN, checksum);
        return new HttpEntity<>(response, headers);
    }

}
