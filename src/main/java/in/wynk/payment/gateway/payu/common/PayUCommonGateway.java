package in.wynk.payment.gateway.payu.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.dto.ICacheService;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Component
public class PayUCommonGateway {

    @Value("${payment.encKey}")
    public String ENC_KEY;
    @Value("${payment.merchant.payu.api.info}")
    public String INFO_API;
    @Value("${payment.merchant.payu.api.payment}")
    public String PAYMENT_API;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    @Getter
    private final ICacheService<PaymentMethod, String> cache;

    public PayUCommonGateway(@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ObjectMapper objectMapper, ICacheService<PaymentMethod, String> cache) {
        this.cache = cache;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public  <T> T exchange(String uri, MultiValueMap<String, String> request, TypeReference<T> target) {
        try {
            final String response = restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(uri)).body(request), String.class).getBody();
            return objectMapper.readValue(response, target);
        } catch (HttpStatusCodeException ex) {
            throw new WynkRuntimeException(PAY015, ex);
        } catch (Exception ex) {
            throw new WynkRuntimeException(PAY015, ex);
        }
    }

    public MultiValueMap<String, String> buildPayUInfoRequest(String client, String command, String var1, String... vars) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String hash = generateHashForPayUApi(payUMerchantKey, payUMerchantSecret, command, var1);
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, command);
        requestMap.add(PAYU_HASH, hash);
        requestMap.add(PAYU_VARIABLE1, var1);
        if (!ArrayUtils.isEmpty(vars)) {
            for (int i = 0; i < vars.length; i++) {
                if (StringUtils.isNotEmpty(vars[i])) {
                    requestMap.add(PAYU_VARIABLE.concat(String.valueOf(i + 2)), vars[i]);
                }
            }
        }
        return requestMap;
    }

    public String generateHashForPayUApi(String payUMerchantKey, String payUSalt, String command, String var1) {
        String builder = payUMerchantKey + PIPE_SEPARATOR +
                command +
                PIPE_SEPARATOR +
                var1 +
                PIPE_SEPARATOR +
                payUSalt;
        return EncryptionUtils.generateSHA512Hash(builder);
    }

}
