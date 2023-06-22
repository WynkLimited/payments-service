package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.aps.common.ApsFailureResponse;
import in.wynk.payment.dto.aps.common.ApsResponseWrapper;
import in.wynk.payment.dto.aps.common.CardDetails;
import in.wynk.payment.dto.aps.request.status.refund.RefundStatusRequest;
import in.wynk.payment.dto.aps.response.refund.ExternalPaymentRefundStatusResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.vas.client.service.ApsClientService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.core.constant.PaymentConstants.BANK_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_MODE;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_REFUND_STATUS;
import static in.wynk.payment.dto.aps.common.ApsConstant.AIRTEL_PAY_STACK;

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
    private EncryptionUtils.RSA rsa;
    private final ObjectMapper objectMapper;
    private final RestTemplate httpTemplate;
    private final ResourceLoader resourceLoader;
    private final ApsClientService apsClientService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService cachingService;


    public ApsCommonGatewayService (ResourceLoader resourceLoader, ApsClientService apsClientService, Gson gson, ApplicationEventPublisher eventPublisher,
                                    @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, ObjectMapper objectMapper, PaymentCachingService cachingService) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.httpTemplate = httpTemplate;
        this.resourceLoader = resourceLoader;
        this.apsClientService = apsClientService;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        final Resource resource = this.resourceLoader.getResource(RSA_PUBLIC_KEY);
        rsa = new EncryptionUtils.RSA(EncryptionUtils.RSA.KeyReader.readPublicKey(resource.getFile()));
    }

    public <T> T exchange (String clientAlias, String url, HttpMethod method, String loginId, Object body, Class<T> target) {
        if (StringUtils.isEmpty(clientAlias)) {
            log.error("client is not loaded for url {}", clientAlias);
            throw new WynkRuntimeException(PAY044);
        }
        try {
            ResponseEntity<String> responseEntity = apsClientService.apsOperations(loginId, generateToken(clientAlias), url, method, body);
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                ApsResponseWrapper apsVasResponse = gson.fromJson(responseEntity.getBody(), ApsResponseWrapper.class);
                if (HttpStatus.OK.name().equals(apsVasResponse.getStatusCode())) {
                    return objectMapper.convertValue(apsVasResponse.getBody(), target);
                }
                ApsFailureResponse failureResponse = objectMapper.readValue((String) apsVasResponse.getBody(), ApsFailureResponse.class);
                failureResponse.setStatusCode(apsVasResponse.getStatusCode());
                throw new WynkRuntimeException(failureResponse.getErrorCode(), failureResponse.getErrorMessage(), failureResponse.getStatusCode());
            }
            throw new WynkRuntimeException(PAY041, responseEntity.getStatusCode().name());
        } catch (JsonProcessingException ex) {
            throw new WynkRuntimeException("Unknown Object from ApsGateway", ex);
        } catch (Exception e) {
            if (e instanceof WynkRuntimeException) {
                throw e;
            }
            throw new WynkRuntimeException(PAY041, e);
        }
    }

    private String generateToken (String clientAlias) {
        final String username = PropertyResolverUtils.resolve(clientAlias, AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
        final String password = PropertyResolverUtils.resolve(clientAlias, AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
        return AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
    }

    public void syncRefundTransactionFromSource (Transaction transaction, String refundId) {
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        try {
            final RefundStatusRequest refundStatusRequest = RefundStatusRequest.builder().refundId(refundId).build();
            mBuilder.request(refundStatusRequest);
            ExternalPaymentRefundStatusResponse externalPaymentRefundStatusResponse = exchange(transaction.getClientAlias(), REFUND_STATUS_ENDPOINT, HttpMethod.POST, getLoginId(transaction.getMsisdn()), refundStatusRequest, ExternalPaymentRefundStatusResponse.class);
            mBuilder.response(externalPaymentRefundStatusResponse);
            mBuilder.externalTransactionId(externalPaymentRefundStatusResponse.getRefundId());
            AnalyticService.update(BaseConstants.EXTERNAL_TRANSACTION_ID, externalPaymentRefundStatusResponse.getRefundId());

            if (!StringUtils.isEmpty(externalPaymentRefundStatusResponse.getRefundStatus()) && externalPaymentRefundStatusResponse.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(externalPaymentRefundStatusResponse.getRefundStatus()) && externalPaymentRefundStatusResponse.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (HttpStatusCodeException e) {
            mBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PAY998, e);
        } catch (Exception e) {
            if (e instanceof WynkRuntimeException) {
                log.error(APS_REFUND_STATUS, e.getMessage());
                throw new WynkRuntimeException(((WynkRuntimeException) e).getErrorCode(), ((WynkRuntimeException) e).getErrorTitle(), e.getMessage());
            }
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
            builder.request(uri);
            ApsChargeStatusResponse[] apsChargeStatusResponses = exchange(transaction.getClientAlias(), uri.toString(), HttpMethod.GET, getLoginId(transaction.getMsisdn()), null, ApsChargeStatusResponse[].class);
            builder.response(apsChargeStatusResponses);
            if (StringUtils.isNotEmpty(apsChargeStatusResponses[0].getPaymentMode())) {
                AnalyticService.update(PAYMENT_MODE, apsChargeStatusResponses[0].getPaymentMode());
            }
            if (StringUtils.isNotEmpty(apsChargeStatusResponses[0].getBankCode())) {
                AnalyticService.update(BANK_CODE, apsChargeStatusResponses[0].getBankCode());
            }
            if (StringUtils.isNotEmpty(apsChargeStatusResponses[0].getCardNetwork())) {
                AnalyticService.update(ApsConstant.APS_CARD_TYPE, apsChargeStatusResponses[0].getCardNetwork());
            }
            builder.externalTransactionId(apsChargeStatusResponses[0].getPgId());
            AnalyticService.update(BaseConstants.EXTERNAL_TRANSACTION_ID, apsChargeStatusResponses[0].getPgId());
            syncTransactionWithSourceResponse(apsChargeStatusResponses[0]);
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(apsChargeStatusResponses[0].getErrorCode()) || !StringUtils.isEmpty(apsChargeStatusResponses[0].getErrorDescription())) {
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(apsChargeStatusResponses[0].getErrorCode()).description(apsChargeStatusResponses[0].getErrorDescription()).build());
                }
            }

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

    private void syncTransactionWithSourceResponse (ApsChargeStatusResponse apsChargeStatusResponse) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        final Transaction transaction = TransactionContext.get();
        int retryInterval = cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
        if ("PAYMENT_SUCCESS".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus())) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
            evict(transaction.getMsisdn());
        } else if ("PAYMENT_FAILED".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus()) || ("PG_FAILED".equalsIgnoreCase(apsChargeStatusResponse.getPgStatus()))) {
            finalTransactionStatus = TransactionStatus.FAILURE;
        } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (BaseConstants.ONE_DAY_IN_MILLI * retryInterval)) &&
                (StringUtils.equalsIgnoreCase("PAYMENT_PENDING", apsChargeStatusResponse.getPaymentStatus()) ||
                        (transaction.getType() == PaymentEvent.REFUND && StringUtils.equalsIgnoreCase("PAYMENT_QUEUED", apsChargeStatusResponse.getPaymentStatus())))) {
            finalTransactionStatus = TransactionStatus.INPROGRESS;
        } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (BaseConstants.ONE_DAY_IN_MILLI * retryInterval)) &&
                StringUtils.equalsIgnoreCase("PAYMENT_PENDING", apsChargeStatusResponse.getPaymentStatus())) {
            finalTransactionStatus = TransactionStatus.INPROGRESS;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
    }

    @CacheEvict(cacheName = "APS_ELIGIBILITY_API", cacheKey = "#msisdn", cacheManager = L2CACHE_MANAGER)
    private void evict (String msisdn) {
    }

    @SneakyThrows
    public String encryptCardData (CardDetails credentials) {
        return rsa.encrypt(gson.toJson(credentials));
    }

    public String getLoginId (String msisdn) {
        return msisdn.replace("+91", "");
    }
}
