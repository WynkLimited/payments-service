package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.core.constant.ClientLoggingMarker;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.utils.ChecksumUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.ClientCallbackPayloadWrapper;
import in.wynk.payment.service.IClientCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Service
public class ClientCallbackServiceImpl implements IClientCallbackService {

    private final RestTemplate restTemplate;

    public ClientCallbackServiceImpl(@Qualifier(BeanConstant.EXTERNAL_PAYMENT_CLIENT_S2S_TEMPLATE) RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @ClientAware(clientAlias = "#callbackPayloadWrapper.clientAlias")
    public <T> void sendCallback(ClientCallbackPayloadWrapper<T> callbackPayloadWrapper) {
        AnalyticService.update(callbackPayloadWrapper.getPayload());
        Client client = ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
        AnalyticService.update(BaseConstants.CLIENT_ID, client.getAlias());
        Optional<Boolean> callbackOptional = client.getMeta(BaseConstants.CALLBACK_ENABLED);
        AnalyticService.update(BaseConstants.CALLBACK_ENABLED, callbackOptional.orElse(false));
        if (callbackOptional.isPresent() && callbackOptional.get()) {
            Optional<String> callbackUrlOptional = client.getMeta(BaseConstants.CALLBACK_URL);
            if (callbackUrlOptional.isPresent()) {
                RequestEntity<T> requestHttpEntity = ChecksumUtils.buildEntityWithChecksum(callbackUrlOptional.get(), client.getClientId(), client.getClientSecret(), callbackPayloadWrapper.getPayload(), HttpMethod.POST);
                AnalyticService.update(BaseConstants.CLIENT_REQUEST, requestHttpEntity.toString());
                try {
                    ResponseEntity<String> partnerResponse = restTemplate.exchange(requestHttpEntity, String.class);
                    AnalyticService.update(BaseConstants.CLIENT_RESPONSE, partnerResponse.toString());
                } catch (HttpStatusCodeException exception) {
                    AnalyticService.update(BaseConstants.CLIENT_RESPONSE, exception.getResponseBodyAsString());
                } catch (Exception exception) {
                    log.error(ClientLoggingMarker.CLIENT_COMMUNICATION_ERROR, exception.getMessage() + " for client " + client.getAlias(), exception);
                }
            } else {
                log.warn(PaymentLoggingMarker.CLIENT_CALLBACK_INFO, "Callback url is not provided despite callback is enabled for client {}", client.getAlias());
            }
        }
    }

}
