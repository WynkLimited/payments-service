package in.wynk.payment.gateway.aps.common;

import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.aps.common.ApsResponseWrapper;
import in.wynk.payment.dto.aps.common.CardDetails;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.vas.client.service.ApsClientService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
    private final ResourceLoader resourceLoader;
    private final ApsClientService apsClientService;
    private final Gson gson;

    public ApsCommonGateway (ResourceLoader resourceLoader, ApsClientService apsClientService, Gson gson) {
        this.gson = gson;
        this.resourceLoader = resourceLoader;
        this.apsClientService = apsClientService;
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        final Resource resource = this.resourceLoader.getResource(RSA_PUBLIC_KEY);
        rsa = new EncryptionUtils.RSA(EncryptionUtils.RSA.KeyReader.readPublicKey(resource.getFile()));
    }

    public <T> T exchange (String url, HttpMethod method, Object body, Class<T> target) {
        ResponseEntity<String> responseEntity = apsClientService.apsOperations(generateToken(), url, method, body);
        try {
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                ApsResponseWrapper apsVasResponse = gson.fromJson(responseEntity.getBody(), ApsResponseWrapper.class);
               if (HttpStatus.OK.getReasonPhrase().equals(apsVasResponse.getStatusCode())) {
                   return gson.fromJson(gson.toJson(apsVasResponse.getBody()), target);
                }
                log.error(PaymentLoggingMarker.APS_API_FAILURE, apsVasResponse.getBody().toString());
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.APS_API_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PAY041);
        }
        throw new WynkRuntimeException(PAY041);
    }

    private String generateToken () {
        final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
        final String username = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
        final String password = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
        return AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    public String encryptCardData (CardDetails credentials) {
        return rsa.encrypt(gson.toJson(credentials));
    }
}
