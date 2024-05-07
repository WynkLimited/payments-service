package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.ICacheService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.payu.AbstractPayUTransactionDetails;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.PayURefundTransactionDetails;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.payment.utils.RecurringTransactionUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_API_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
@Component
public class PayUCommonGateway {

    @Value("${payment.encKey}")
    public String ENC_KEY;
    @Value("${payment.merchant.payu.api.info}")
    public String INFO_API;
    @Value("${payment.merchant.payu.api.payment}")
    public String PAYMENT_API;

    private final RestTemplate restTemplate;
    @Getter
    private final ObjectMapper mapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RecurringTransactionUtils recurringTransactionUtils;
    @Getter
    private final ICacheService<PaymentMethod, String> cache;

    public PayUCommonGateway (@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ObjectMapper objectMapper, ICacheService<PaymentMethod, String> cache,
                              PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, RecurringTransactionUtils recurringTransactionUtils) {
        this.cache = cache;
        this.restTemplate = restTemplate;
        this.mapper = objectMapper;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.recurringTransactionUtils = recurringTransactionUtils;
    }

    public <T> T exchange (String uri, MultiValueMap<String, String> request, TypeReference<T> target) {
        return exchange(uri, request, new HttpHeaders(), target);
    }

    public <T> T exchange (String uri, MultiValueMap<String, String> request, HttpHeaders headers, TypeReference<T> target) {
        try {
            final String response = restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(uri)).body(request), String.class).getBody();
            if (StringUtils.isNotEmpty(response) && response.contains("Record not found")) {
                throw new WynkRuntimeException("Record not found");
            }
            return mapper.readValue(response, target);
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        }
    }

    public MultiValueMap<String, String> buildPayUInfoRequest (String client, String command, String var1, String... vars) {
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

    public String generateHashForPayUApi (String payUMerchantKey, String payUSalt, String command, String var1) {
        String builder = payUMerchantKey + PIPE_SEPARATOR +
                command +
                PIPE_SEPARATOR +
                var1 +
                PIPE_SEPARATOR +
                payUSalt;
        return EncryptionUtils.generateSHA512Hash(builder);
    }

    public PayUVerificationResponse<PayUChargingTransactionDetails> syncChargingTransactionFromSource (Transaction transaction) {
        final MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse;
        try {
            final MultiValueMap<String, String> payUChargingVerificationRequest =
                    buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
            merchantTransactionEventBuilder.request(payUChargingVerificationRequest);
            payUChargingVerificationResponse = exchange(INFO_API, payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
            });
            if (Objects.isNull(payUChargingVerificationResponse.getTransactionDetails())) {
                throw new WynkRuntimeException("Failed to sync transaction from payu with error: " + payUChargingVerificationResponse.getMessage());
            }
            merchantTransactionEventBuilder.response(payUChargingVerificationResponse);
            final PayUChargingTransactionDetails payUChargingTransactionDetails = payUChargingVerificationResponse.getTransactionDetails(transaction.getId().toString());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getMode())) {
                AnalyticService.update(PAYMENT_MODE, payUChargingTransactionDetails.getMode());
            }
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getBankCode())) {
                AnalyticService.update(BANK_CODE, payUChargingTransactionDetails.getBankCode());
            }
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getCardType())) {
                AnalyticService.update(PAYU_CARD_TYPE, payUChargingTransactionDetails.getCardType());
            }
            merchantTransactionEventBuilder.externalTransactionId(payUChargingTransactionDetails.getPayUExternalTxnId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payUChargingTransactionDetails.getPayUExternalTxnId());
            syncTransactionWithSourceResponse(transaction, payUChargingVerificationResponse);
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(payUChargingTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUChargingTransactionDetails.getErrorMessage())) {
                    recurringTransactionUtils.cancelRenewalBasedOnErrorReason(payUChargingTransactionDetails.getErrorMessage(), transaction);
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr())
                            .code(Objects.nonNull(payUChargingTransactionDetails.getErrorCode()) ? payUChargingTransactionDetails.getErrorCode() : "UNKNOWN")
                            .description(payUChargingTransactionDetails.getErrorMessage()).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
        eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        return payUChargingVerificationResponse;
    }

    public void syncRefundTransactionFromSource (Transaction transaction, String refundRequestId) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            MultiValueMap<String, String> payURefundStatusRequest = buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.CHECK_ACTION_STATUS.getCode(), refundRequestId);
            merchantTransactionEventBuilder.request(payURefundStatusRequest);
            PayUVerificationResponse<Map<String, PayURefundTransactionDetails>>
                    payUPaymentRefundResponse = exchange(INFO_API, payURefundStatusRequest, new TypeReference<PayUVerificationResponse<Map<String, PayURefundTransactionDetails>>>() {
            });
            merchantTransactionEventBuilder.response(payUPaymentRefundResponse);
            Map<String, PayURefundTransactionDetails> payURefundTransactionDetails = payUPaymentRefundResponse.getTransactionDetails(refundRequestId);
            merchantTransactionEventBuilder.externalTransactionId(payURefundTransactionDetails.get(refundRequestId).getRequestId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payURefundTransactionDetails.get(refundRequestId).getRequestId());
            payURefundTransactionDetails.put(transaction.getIdStr(), payURefundTransactionDetails.get(refundRequestId));
            payURefundTransactionDetails.remove(refundRequestId);
            syncTransactionWithSourceResponse(transaction,
                    PayUVerificationResponse.<PayURefundTransactionDetails>builder().transactionDetails(payURefundTransactionDetails).message(payUPaymentRefundResponse.getMessage())
                            .status(payUPaymentRefundResponse.getStatus()).build());
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
        if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
            syncChargingTransactionFromSource(transaction);
        } else {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    public void syncTransactionWithSourceResponse (Transaction transaction, PayUVerificationResponse<? extends AbstractPayUTransactionDetails> transactionDetailsWrapper) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        int retryInterval = (transaction.getType() == PaymentEvent.POINT_PURCHASE) ? 1 : cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
        if (transactionDetailsWrapper.getStatus() == 1) {
            final AbstractPayUTransactionDetails transactionDetails = transactionDetailsWrapper.getTransactionDetails(transaction.getIdStr());
            if (SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(transactionDetails.getStatus())) ||
                    PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (ONE_DAY_IN_MILLI * retryInterval)) &&
                    (StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus()) ||
                            (transaction.getType() == PaymentEvent.REFUND && StringUtils.equalsIgnoreCase(QUEUED, transactionDetails.getStatus())))) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (ONE_DAY_IN_MILLI * retryInterval)) &&
                    StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
    }

}
