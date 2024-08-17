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
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.event.PaymentRefundInitEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.*;
import in.wynk.payment.dto.aps.request.status.mandate.ApsMandateStatusRequest;
import in.wynk.payment.dto.aps.request.status.refund.RefundStatusRequest;
import in.wynk.payment.dto.aps.response.order.ApsOrderStatusResponse;
import in.wynk.payment.dto.aps.response.order.OrderInfo;
import in.wynk.payment.dto.aps.response.order.OrderPaymentDetails;
import in.wynk.payment.dto.aps.response.status.mandate.ApsMandateStatusResponse;
import in.wynk.payment.dto.aps.response.status.refund.ApsRefundStatusResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.vas.client.service.ApsClientService;
import io.netty.channel.ConnectTimeoutException;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.constant.OrderStatus.*;
import static in.wynk.payment.core.constant.PaymentConstants.BANK_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_MODE;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.aps.common.ApsConstant.*;

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
    @Value("${aps.payment.order.status.api}")
    private String ORDER_STATUS_ENDPOINT;
    @Value("${aps.payment.mandate.status.api}")
    private String MANDATE_STATUS_ENDPOINT;

    private final Gson gson;
    private EncryptionUtils.RSA rsa;
    private final ObjectMapper objectMapper;
    private final RestTemplate httpTemplate;
    private final ResourceLoader resourceLoader;
    private final ApsClientService apsClientService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService cachingService;
    private final IMerchantTransactionService merchantTransactionService;
    private final RecurringTransactionUtils recurringTransactionUtils;


    public ApsCommonGatewayService (ResourceLoader resourceLoader, ApsClientService apsClientService, Gson gson, ApplicationEventPublisher eventPublisher,
                                    @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, ObjectMapper objectMapper, PaymentCachingService cachingService,
                                    IMerchantTransactionService merchantTransactionService, RecurringTransactionUtils recurringTransactionUtils) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.httpTemplate = httpTemplate;
        this.resourceLoader = resourceLoader;
        this.apsClientService = apsClientService;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.merchantTransactionService = merchantTransactionService;
        this.recurringTransactionUtils = recurringTransactionUtils;
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        final Resource resource = this.resourceLoader.getResource(RSA_PUBLIC_KEY);
        rsa = new EncryptionUtils.RSA(EncryptionUtils.RSA.KeyReader.readPublicKey(resource.getFile()));
    }

    public <T> T exchange (String clientAlias, String url, HttpMethod method, String msisdn, Object body, Class<T> target) {
        if (StringUtils.isEmpty(clientAlias)) {
            log.error("client is not loaded for url {}", clientAlias);
            throw new WynkRuntimeException(APS002);
        }
        try {
            ResponseEntity<String> responseEntity = apsClientService.apsOperations(getLoginId(msisdn), generateToken(url, clientAlias), url, method, body);
            log.info("Response Status Code: {}", responseEntity.getStatusCode());
            log.info("Response Body: {}", responseEntity.getBody());
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                ApsResponseWrapper apsVasResponse = gson.fromJson(responseEntity.getBody(), ApsResponseWrapper.class);
                if (HttpStatus.OK.name().equals(apsVasResponse.getStatusCode())) {
                    return objectMapper.convertValue(apsVasResponse.getBody(), target);
                }
                ApsFailureResponse failureResponse = objectMapper.readValue((String) responseEntity.getBody(), ApsFailureResponse.class);
                failureResponse.setStatusCode(apsVasResponse.getStatusCode());
                throw new WynkRuntimeException(failureResponse.getErrorCode(), failureResponse.getMessage(), failureResponse.getStatusCode());
            }
            throw new WynkRuntimeException(APS001, responseEntity.getStatusCode().name());
        } catch (JsonProcessingException ex) {
            throw new WynkRuntimeException("Unknown Object from ApsGateway", ex);
        } catch (Exception e) {
            if (e instanceof WynkRuntimeException) {
                throw e;
            }
            throw new WynkRuntimeException(APS001, e);
        }
    }

    private String generateToken (String url, String clientAlias) {
        final String username = PropertyResolverUtils.resolve(clientAlias, url.contains(PAY_DIGI) ? PAY_DIGI : INT_PAY, AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
        final String password = PropertyResolverUtils.resolve(clientAlias, url.contains(PAY_DIGI) ? PAY_DIGI : INT_PAY, AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
        return AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
    }

    public void syncRefundTransactionFromSource (Transaction transaction, String refundId) {
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        try {
            final RefundStatusRequest refundStatusRequest = RefundStatusRequest.builder().refundId(refundId).build();
            mBuilder.request(refundStatusRequest);
            ApsRefundStatusResponse externalPaymentRefundStatusResponse =
                    exchange(transaction.getClientAlias(), REFUND_STATUS_ENDPOINT, HttpMethod.POST, getLoginId(transaction.getMsisdn()), refundStatusRequest,
                            ApsRefundStatusResponse.class);
            mBuilder.response(externalPaymentRefundStatusResponse);
            mBuilder.externalTransactionId(externalPaymentRefundStatusResponse.getRefundId());
            AnalyticService.update(BaseConstants.EXTERNAL_TRANSACTION_ID, externalPaymentRefundStatusResponse.getRefundId());

            if (!StringUtils.isEmpty(externalPaymentRefundStatusResponse.getRefundStatus()) && externalPaymentRefundStatusResponse.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(externalPaymentRefundStatusResponse.getRefundStatus()) && externalPaymentRefundStatusResponse.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (Exception e) {
            mBuilder.response(e.getMessage());
            if (e instanceof WynkRuntimeException) {
                log.error(APS_REFUND_STATUS, e.getMessage());
                throw new WynkRuntimeException(((WynkRuntimeException) e).getErrorCode(), ((WynkRuntimeException) e).getErrorTitle(), e.getMessage());
            }
            log.error(APS_REFUND_STATUS, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            finalTransactionStatus = TransactionStatus.FAILURE;
            throw new WynkRuntimeException(PAY998, e);
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
                syncChargingTransactionFromSource(transaction, Optional.empty());
            } else {
                eventPublisher.publishEvent(mBuilder.build());
            }
        }
    }

    public ApsChargeStatusResponse[] syncChargingTransactionFromSource (Transaction transaction, Optional<ApsChargeStatusResponse[]> verifyOption) {
        final String txnId = transaction.getIdStr();
        final boolean fetchHistoryTransaction = false;
        final MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        ApsChargeStatusResponse[] apsChargeStatusResponses;
        try {
            final URI uri = httpTemplate.getUriTemplateHandler().expand(CHARGING_STATUS_ENDPOINT, txnId, fetchHistoryTransaction);
            builder.request(uri);
            apsChargeStatusResponses =
                    verifyOption.orElseGet(() -> exchange(transaction.getClientAlias(), uri.toString(), HttpMethod.GET, getLoginId(transaction.getMsisdn()), null, ApsChargeStatusResponse[].class));
            builder.response(apsChargeStatusResponses);
            if (StringUtils.isNotEmpty(apsChargeStatusResponses[0].getPaymentMode())) {
                String paymentMode = apsChargeStatusResponses[0].getPaymentMode();
                if ("CREDIT_CARD".equals(apsChargeStatusResponses[0].getPaymentMode()) || "DEBIT_CARD".equals(apsChargeStatusResponses[0].getPaymentMode())) {
                    paymentMode = "CREDIT_CARD".equals(apsChargeStatusResponses[0].getPaymentMode()) ? "CC" : "DC";
                }
                AnalyticService.update(PAYMENT_MODE, paymentMode);
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
                    recurringTransactionUtils.cancelRenewalBasedOnErrorReason(apsChargeStatusResponses[0].getErrorDescription(), transaction);
                    eventPublisher.publishEvent(
                            PaymentErrorEvent.builder(transaction.getIdStr()).code(apsChargeStatusResponses[0].getErrorCode()).description(apsChargeStatusResponses[0].getErrorDescription()).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            builder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(APS_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to " + e.getMessage());
            throw new WynkRuntimeException(PAY998, e);
        }
        eventPublisher.publishEvent(builder.build());
        return apsChargeStatusResponses;
    }

    private void syncTransactionWithSourceResponse (ApsChargeStatusResponse apsChargeStatusResponse) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        final Transaction transaction = TransactionContext.get();
        if ((!apsChargeStatusResponse.getLob().equals(LOB.AUTO_PAY_REGISTER_WYNK.toString()) && "PAYMENT_SUCCESS".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus())) || (apsChargeStatusResponse.getLob().equals(LOB.AUTO_PAY_REGISTER_WYNK.toString()) && ("PAYMENT_SUCCESS".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus())) && SiRegistrationStatus.ACTIVE == apsChargeStatusResponse.getMandateStatus())) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
            evict(transaction.getMsisdn());
        } else if ((apsChargeStatusResponse.getLob().equals(LOB.AUTO_PAY_REGISTER_WYNK.toString()) && SiRegistrationStatus.ACTIVE != apsChargeStatusResponse.getMandateStatus() && "PAYMENT_SUCCESS".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus())) || ("PAYMENT_FAILED".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus())) || ("PG_FAILED".equalsIgnoreCase(apsChargeStatusResponse.getPgStatus()))) {
            if ("PAYMENT_SUCCESS".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus())) {
                eventPublisher.publishEvent(PaymentRefundInitEvent.builder()
                        .reason("mandate status was not active")
                        .originalTransactionId(transaction.getIdStr())
                        .build());
            }
            finalTransactionStatus = TransactionStatus.FAILURE;
        } else if ("PAYMENT_PENDING".equalsIgnoreCase(apsChargeStatusResponse.getPaymentStatus()) || ("PG_PENDING".equalsIgnoreCase(apsChargeStatusResponse.getPgStatus()))) {
            finalTransactionStatus = TransactionStatus.INPROGRESS;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
    }

    private void syncTransactionWithSourceResponse (OrderInfo orderInfo) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        final Transaction transaction = TransactionContext.get();
        if (ORDER_COMPLETE == orderInfo.getOrderStatus() || ORDER_PARTIAL_COMPLETE == orderInfo.getOrderStatus() || ORDER_MANUAL_COMPLETE == orderInfo.getOrderStatus()) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
            evict(transaction.getMsisdn());
        } else if (ORDER_FAILED == orderInfo.getOrderStatus() || ORDER_CLOSED == orderInfo.getOrderStatus()) {
            finalTransactionStatus = TransactionStatus.FAILURE;
        } else if (ORDER_PROCESSING == orderInfo.getOrderStatus() || ORDER_CREATED == orderInfo.getOrderStatus() || ORDER_AWAITING_EVENT == orderInfo.getOrderStatus()) {
            finalTransactionStatus = TransactionStatus.INPROGRESS;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
    }

    public void syncOrderTransactionFromSource (Transaction transaction) {
        String txnId = transaction.getIdStr();
        final MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(txnId);
        String orderId = merchantTransaction.getOrderId();
        if (StringUtils.isEmpty(orderId)) {
            throw new WynkRuntimeException("Order Id is missing in merchant table which is mandatory for aps_v2");
        }
        final URI uri = httpTemplate.getUriTemplateHandler().expand(ORDER_STATUS_ENDPOINT, orderId);
        builder.request(uri);
        builder.orderId(orderId);
        ApsOrderStatusResponse apsChargeStatusResponse;
        OrderPaymentDetails paymentDetails;
        try {
            apsChargeStatusResponse = exchange(transaction.getClientAlias(), uri.toString(), HttpMethod.GET, null, null, ApsOrderStatusResponse.class);
            builder.response(apsChargeStatusResponse);
            paymentDetails = apsChargeStatusResponse.getPaymentDetails()[0];
            if (StringUtils.isNotEmpty(paymentDetails.getPaymentMode())) {
                String mode = paymentDetails.getPaymentMode();
                if ("CREDIT_CARD".equals(paymentDetails.getPaymentMode()) || "DEBIT_CARD".equals(paymentDetails.getPaymentMode())) {
                    mode = "CREDIT_CARD".equals(paymentDetails.getPaymentMode()) ? "CC" : "DC";
                }
                AnalyticService.update(PAYMENT_MODE, mode);
            }
            if (StringUtils.isNotEmpty(paymentDetails.getBankCode())) {
                AnalyticService.update(BANK_CODE, paymentDetails.getBankCode());
            }
            if (StringUtils.isNotEmpty(paymentDetails.getCardNetwork())) {
                AnalyticService.update(ApsConstant.APS_CARD_TYPE, paymentDetails.getCardNetwork());
            }
            builder.externalTransactionId(paymentDetails.getPgId());
            AnalyticService.update(BaseConstants.EXTERNAL_TRANSACTION_ID, paymentDetails.getPgId());
            syncTransactionWithSourceResponse(apsChargeStatusResponse.getOrderInfo());
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(paymentDetails.getErrorCode()) || !StringUtils.isEmpty(paymentDetails.getErrorDescription())) {
                    recurringTransactionUtils.cancelRenewalBasedOnErrorReason(paymentDetails.getErrorDescription(), transaction);
                    eventPublisher.publishEvent(
                            PaymentErrorEvent.builder(transaction.getIdStr()).code(paymentDetails.getErrorCode())
                                    .description(paymentDetails.getErrorDescription()).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            builder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(APS_ORDER_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to " + e.getMessage());
            throw new WynkRuntimeException(PAY998, e);
        }
        if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE) {
            eventPublisher.publishEvent(builder.build());
        }

    }

    @CacheEvict(cacheName = "APS_ELIGIBILITY_API", cacheKey = "#msisdn", cacheManager = L2CACHE_MANAGER)
    private void evict (String msisdn) {
    }

    @SneakyThrows
    public String encryptCardData (CardDetails credentials) {
        return rsa.encrypt(gson.toJson(credentials));
    }

    public String getLoginId (String msisdn) {
        return msisdn != null ? msisdn.replace("+91", "") : null;
    }

    public boolean isMandateActive (Transaction transaction, String mandateId, String merchantId) {
        ApsMandateStatusRequest request = ApsMandateStatusRequest.builder().mandateId(mandateId).build();
        ApsMandateStatusResponse apsMandateStatusResponse;
        try {
            apsMandateStatusResponse = exchange(transaction.getClientAlias(), MANDATE_STATUS_ENDPOINT, HttpMethod.POST, null, request, ApsMandateStatusResponse.class);
        } catch (RestClientException e) {
            if (e.getRootCause() != null && e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException) {
                log.error(APS_MANDATE_STATUS_VALIDATION_ERROR, "Socket timeout during mandate validation but retry will happen {}, {} ", request, e.getMessage(), e);
                throw new WynkRuntimeException(APS013);
            }
            return true;
        } catch (Exception ex) {
            log.error(APS_MANDATE_STATUS_VALIDATION_ERROR, ex.getMessage(), ex);
            throw new WynkRuntimeException(APS013, ex);
        }
        boolean isMandateActive = false;
        if (apsMandateStatusResponse != null) {
            isMandateActive = "active".equalsIgnoreCase(apsMandateStatusResponse.getStatus());
            if (!isMandateActive) {
                String errorReason = "mandate status is: " + apsMandateStatusResponse.getStatus().toLowerCase(Locale.ROOT);
                AnalyticService.update(PaymentConstants.ERROR_REASON, errorReason);
                recurringTransactionUtils.cancelRenewalBasedOnErrorReason(errorReason, transaction);
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(APS013.getErrorCode()).description(errorReason).build());

            }
        }
        return isMandateActive;
    }
}
