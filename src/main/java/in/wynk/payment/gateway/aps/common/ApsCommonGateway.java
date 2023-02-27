package in.wynk.payment.gateway.aps.common;

import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.aps.common.CardDetails;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY041;

/**
 * @author Nishesh Pandey
 */

@Slf4j
@Component
public class ApsCommonGateway {
    @Value("${aps.payment.encryption.key.path}")
    private String RSA_PUBLIC_KEY;


    private EncryptionUtils.RSA rsa;
    private final RestTemplate httpTemplate;
    private final ResourceLoader resourceLoader;
    private final Gson gson;

    public ApsCommonGateway (@Qualifier("apsHttpTemplate") RestTemplate httpTemplate, ResourceLoader resourceLoader, Gson gson) {
        this.httpTemplate = httpTemplate;
        this.resourceLoader = resourceLoader;
        this.gson = gson;
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        final Resource resource = this.resourceLoader.getResource(RSA_PUBLIC_KEY);
        rsa = new EncryptionUtils.RSA(EncryptionUtils.RSA.KeyReader.readPublicKey(resource.getFile()));
        this.httpTemplate.getInterceptors().add((request, body, execution) -> {
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
            final String username = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
            final String password = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
            final String token = AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
            request.getHeaders().add(CHANNEL_ID, AUTH_TYPE_WEB_UNAUTH);
            request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            return execution.execute(request, body);
        });
    }

    public <T> T exchange (RequestEntity<?> entity, ParameterizedTypeReference<T> target) {
        try {
            return httpTemplate.exchange(entity, target).getBody();
        } catch (Exception e) {
            throw new WynkRuntimeException(PAY041, e);
        }
    }

    @SneakyThrows
    public String encryptCardData (CardDetails credentials) {
        return rsa.encrypt(gson.toJson(credentials));
    }
}
