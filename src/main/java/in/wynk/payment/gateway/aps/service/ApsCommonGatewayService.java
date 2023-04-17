package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.client.context.ClientContext;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsFailureResponse;
import in.wynk.payment.dto.aps.common.ApsResponseWrapper;
import in.wynk.payment.dto.aps.common.CardDetails;
import in.wynk.payment.dto.aps.request.status.refund.RefundStatusRequest;
import in.wynk.payment.dto.aps.response.refund.ExternalPaymentRefundStatusResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.vas.client.service.ApsClientService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY041;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY998;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_REFUND_STATUS;

/**
 * @author Nishesh Pandey
 */

@Slf4j
@Service
public class ApsCommonGatewayService {
    @Value("${aps.payment.encryption.key.path}")
    private String RSA_PUBLIC_KEY;
    @Value("${aps.payment.refund.status.api}")
    private String REFUND_STATUS_ENDPOINT;
    @Value("${aps.payment.charge.status.api}")
    private String CHARGING_STATUS_ENDPOINT;

    private final Gson gson;
    private final ObjectMapper objectMapper;
    private final RestTemplate httpTemplate;
    private EncryptionUtils.RSA rsa;
    private final ResourceLoader resourceLoader;
    private final ApsClientService apsClientService;
    private final ApplicationEventPublisher eventPublisher;


    public ApsCommonGatewayService (ResourceLoader resourceLoader, ApsClientService apsClientService, Gson gson, ApplicationEventPublisher eventPublisher,
                                    @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, ObjectMapper objectMapper) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.httpTemplate = httpTemplate;
        this.resourceLoader = resourceLoader;
        this.apsClientService = apsClientService;
        this.eventPublisher = eventPublisher;
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        final Resource resource = this.resourceLoader.getResource(RSA_PUBLIC_KEY);
        rsa = new EncryptionUtils.RSA(EncryptionUtils.RSA.KeyReader.readPublicKey(resource.getFile()));
    }

    public <T> T exchange (String url, HttpMethod method, String loginId, Object body, Class<T> target) {
        ResponseEntity<String> responseEntity = apsClientService.apsOperations(loginId, generateToken(), url, method, body);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            try {
                ApsResponseWrapper apsVasResponse = gson.fromJson(responseEntity.getBody(), ApsResponseWrapper.class);
                if (HttpStatus.OK.name().equals(apsVasResponse.getStatusCode())) {
                    return objectMapper.convertValue(apsVasResponse.getBody(), target);
                }
                ApsFailureResponse failureResponse = objectMapper.readValue((String) apsVasResponse.getBody(), ApsFailureResponse.class);
                throw new WynkRuntimeException(failureResponse.getErrorCode(), failureResponse.getErrorMessage(), "APS API Error");
            } catch (JsonProcessingException ex) {
                throw new WynkRuntimeException("Unknown Object from ApsGateway", ex);
            }
        }
        throw new WynkRuntimeException(PAY041);
    }

    private String generateToken () {
        String clientAlias;
        try {
            final Transaction transaction = TransactionContext.get();
            clientAlias = transaction.getClientAlias();
        } catch (NullPointerException e) {
            clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
        }
        final String username = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
        final String password = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
        return AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
    }

    public void syncRefundTransactionFromSource (Transaction transaction, String refundId) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final RefundStatusRequest refundStatusRequest = RefundStatusRequest.builder().refundId(refundId).build();
            ExternalPaymentRefundStatusResponse body =
                    exchange(REFUND_STATUS_ENDPOINT, HttpMethod.POST, getLoginId(transaction.getMsisdn()), refundStatusRequest, ExternalPaymentRefundStatusResponse.class);
            mBuilder.request(refundStatusRequest);
            mBuilder.response(body);
            mBuilder.externalTransactionId(body.getRefundId());
            if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }

        } catch (HttpStatusCodeException e) {
            mBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PAY998, e);
        } catch (Exception e) {
            log.error(APS_REFUND_STATUS, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PAY998, e);
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            eventPublisher.publishEvent(mBuilder.build());
        }

    }

    public void syncChargingTransactionFromSource (Transaction transaction) {
        final String txnId = transaction.getIdStr();
        final boolean fetchHistoryTransaction = false;
        final MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final URI uri = httpTemplate.getUriTemplateHandler().expand(CHARGING_STATUS_ENDPOINT, txnId, fetchHistoryTransaction);

            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<RefundStatusRequest> requestEntity = new RequestEntity<>(null, headers, HttpMethod.GET, URI.create(uri.toString()));

            ApsChargeStatusResponse[] status = exchange(uri.toString(), HttpMethod.GET, getLoginId(transaction.getMsisdn()), null, ApsChargeStatusResponse[].class);

            if (status[0].getPaymentStatus().equalsIgnoreCase("PAYMENT_SUCCESS")) {
                transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                evict(transaction.getMsisdn());
            } else if (status[0].getPaymentStatus().equalsIgnoreCase("PAYMENT_FAILED")) {
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
            builder.request(status).response(status);
            builder.externalTransactionId(status[0].getPgId());

        } catch (HttpStatusCodeException e) {
            builder.request(e.getResponseBodyAsString()).response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PAY998, e);
        } catch (Exception e) {
            log.error(APS_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE) {
                eventPublisher.publishEvent(builder.build());
            }
        }
    }

    @CacheEvict(cacheName = "APS_ELIGIBILITY_API", cacheKey = "#msisdn", cacheManager = L2CACHE_MANAGER)
    private void evict(String msisdn) { }

    @SneakyThrows
    public String encryptCardData (CardDetails credentials) {
        return rsa.encrypt(gson.toJson(credentials));
    }

    public String getLoginId (String msisdn) {
        return msisdn.replace("+91", "");
    }
}
