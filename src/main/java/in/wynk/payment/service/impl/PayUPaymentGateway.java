package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.ICacheService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.dto.payu.external.charge.upi.intent.PayUUpiIntentExternalChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.PayUGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.card.AbstractPayUCardGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.card.redirection.PayUCardGatewayNonSeamlessChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.card.seamless.PayUCardGatewaySeamlessChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.netbanking.AbstractPayUNetBankingGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.netbanking.PayUNetBankingGatewayNonSeamlessChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.netbanking.PayUNetBankingGatewaySeamlessChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.upi.AbstractPayUUpiGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.upi.PayUUpiCollectGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.upi.PayUUpiIntentGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.wallet.AbstractPayUWalletGatewayChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.wallet.PayUWalletGatewayNonSeamlessChargingResponse;
import in.wynk.payment.dto.payu.internal.charge.wallet.PayUWalletGatewaySeamlessChargingResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.service.IPaymentChargingService;
import in.wynk.payment.service.IPaymentStatusService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.COLON;
import static in.wynk.payment.core.constant.BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY889;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.payu.PayUConstants.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PAYU_MERCHANT_PAYMENT_SERVICE + VERSION_2)
public class PayUPaymentGateway implements IPaymentChargingService<PayUGatewayChargingResponse, PayUChargingRequest<?>>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

    private final IErrorCodesCacheService errorCodesCacheServiceImpl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ICacheService<PaymentMethod, String> paymentMethodCachingService;
    private final Map<String, IPaymentChargingService<? extends PayUGatewayChargingResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();
    private final Map<Class<? extends AbstractTransactionReconciliationStatusRequest>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>> statusDelegate = new HashMap<>();

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;
    @Value("${payment.merchant.payu.api.payment}")
    private String payUPaymentApiUrl;
    @Value("${payment.encKey}")
    private String encryptionKey;

    public PayUPaymentGateway(@Qualifier(EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ICacheService<PaymentMethod, String> paymentMethodCachingService,IErrorCodesCacheService errorCodesCacheServiceImpl, ObjectMapper objectMapper, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher) {
        this.restTemplate = restTemplate;
        this.chargeDelegate.put("UPI", new Upi());
        this.chargeDelegate.put("CARD", new Card());
        this.chargeDelegate.put("WALLET", new Wallet());
        this.chargeDelegate.put("NET_BANKING", new NetBanking());
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.errorCodesCacheServiceImpl = errorCodesCacheServiceImpl;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.statusDelegate.put(ChargingTransactionReconciliationStatusRequest.class, new ChargingTransactionReconciliationStatusService());
        this.statusDelegate.put(RefundTransactionReconciliationStatusRequest.class, new RefundTransactionReconciliationStatusService());
    }

    @Override
    public PayUGatewayChargingResponse charge(PayUChargingRequest<?> request) {
        final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
        final String paymentGroup = paymentMethod.getGroup();
        return chargeDelegate.get(paymentGroup).charge(request);
    }

    private Map<String, String> getPayload(PayUChargingRequest<?> chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        final int planId = transaction.getPlanId();
        double finalPlanAmount = transaction.getAmount();
        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), transaction.getPaymentChannel().getCode().toLowerCase(), MERCHANT_ID);
        String userCredentials = payUMerchantKey + COLON + uid;
        Map<String, String> payloadTemp;
        payloadTemp = getPayload(transaction.getClientAlias(), transaction.getId(), email, uid, planId, finalPlanAmount);
        // Mandatory according to document
        Map<String, String> payload = new HashMap<>(payloadTemp);
        payload.put(PAYU_MERCHANT_KEY, payUMerchantKey);
        payload.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString());
        payload.put(PAYU_TRANSACTION_AMOUNT, String.valueOf(finalPlanAmount));
        payload.put(PAYU_PRODUCT_INFO, String.valueOf(planId));
        payload.put(PAYU_CUSTOMER_FIRSTNAME, uid);
        payload.put(PAYU_CUSTOMER_EMAIL, email);
        payload.put(PAYU_CUSTOMER_MSISDN, msisdn);
        payload.put(PAYU_SUCCESS_URL, ((IChargingDetails) chargingRequest.getPurchaseDetails()).getCallbackDetails().getCallbackUrl());
        payload.put(PAYU_FAILURE_URL, ((IChargingDetails) chargingRequest.getPurchaseDetails()).getCallbackDetails().getCallbackUrl());
        // Not in document
        payload.put(PAYU_IS_FALLBACK_ATTEMPT, String.valueOf(false));
        payload.put(ERROR, PAYU_REDIRECT_MESSAGE);
        payload.put(PAYU_USER_CREDENTIALS, userCredentials);
        return payload;
    }

    private Map<String, String> getPayload(String client, UUID transactionId, String email, String uid, int planId, double finalPlanAmount) {
        Map<String, String> payload = new HashMap<>();
        String udf1 = StringUtils.EMPTY;
        String reqType = PaymentRequestType.DEFAULT.name();
        String checksumHash = getChecksumHashForPayment(client, transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount);
        payload.put(PAYU_HASH, checksumHash);
        payload.put(PAYU_REQUEST_TYPE, reqType);
        payload.put(PAYU_UDF1_PARAMETER, udf1);
        return payload;
    }

    private String getChecksumHashForPayment(String client, UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String rawChecksum = payUMerchantKey
                + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle
                + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + payUMerchantSecret;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

    @Override
    public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> reconStatusService =
                statusDelegate.get(request.getClass());
        if (Objects.isNull(reconStatusService)){
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
        return reconStatusService.status(request);
    }

    public void syncChargingTransactionFromSource (Transaction transaction, Optional<String> failureReasonOption) {
        final MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
            merchantTransactionEventBuilder.request(payUChargingVerificationRequest);
            final PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
            });
            merchantTransactionEventBuilder.response(payUChargingVerificationResponse);
            final PayUChargingTransactionDetails payUChargingTransactionDetails = payUChargingVerificationResponse.getTransactionDetails(transaction.getId().toString());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getMode()))
                AnalyticService.update(PAYMENT_MODE, payUChargingTransactionDetails.getMode());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getBankCode()))
                AnalyticService.update(BANK_CODE, payUChargingTransactionDetails.getBankCode());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getCardType()))
                AnalyticService.update(PAYU_CARD_TYPE, payUChargingTransactionDetails.getCardType());
            merchantTransactionEventBuilder.externalTransactionId(payUChargingTransactionDetails.getPayUExternalTxnId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payUChargingTransactionDetails.getPayUExternalTxnId());
            syncTransactionWithSourceResponse(payUChargingVerificationResponse);
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(payUChargingTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUChargingTransactionDetails.getTransactionFailureReason())) {
                    final String failureReason = failureReasonOption.orElse(payUChargingTransactionDetails.getTransactionFailureReason());
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(failureReason).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE)
                eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private void syncTransactionWithSourceResponse (PayUVerificationResponse<? extends AbstractPayUTransactionDetails> transactionDetailsWrapper) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        final Transaction transaction = TransactionContext.get();
        int retryInterval = cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
        if (transactionDetailsWrapper.getStatus() == 1) {
            final AbstractPayUTransactionDetails transactionDetails = transactionDetailsWrapper.getTransactionDetails(transaction.getIdStr());
            if (SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(transactionDetails.getStatus())) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (ONE_DAY_IN_MILLI * retryInterval)) &&
                    (StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus()) || (transaction.getType() == PaymentEvent.REFUND && StringUtils.equalsIgnoreCase(QUEUED, transactionDetails.getStatus())))) {
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

    private void syncRefundTransactionFromSource (Transaction transaction, String refundRequestId) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            MultiValueMap<String, String> payURefundStatusRequest = this.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.CHECK_ACTION_STATUS.getCode(), refundRequestId);
            merchantTransactionEventBuilder.request(payURefundStatusRequest);
            PayUVerificationResponse<Map<String, PayURefundTransactionDetails>> payUPaymentRefundResponse = this.getInfoFromPayU(payURefundStatusRequest, new TypeReference<PayUVerificationResponse<Map<String, PayURefundTransactionDetails>>>() {
            });
            merchantTransactionEventBuilder.response(payUPaymentRefundResponse);
            Map<String, PayURefundTransactionDetails> payURefundTransactionDetails = payUPaymentRefundResponse.getTransactionDetails(refundRequestId);
            merchantTransactionEventBuilder.externalTransactionId(payURefundTransactionDetails.get(refundRequestId).getRequestId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payURefundTransactionDetails.get(refundRequestId).getRequestId());
            payURefundTransactionDetails.put(transaction.getIdStr(), payURefundTransactionDetails.get(refundRequestId));
            payURefundTransactionDetails.remove(refundRequestId);
            syncTransactionWithSourceResponse(PayUVerificationResponse.<PayURefundTransactionDetails>builder().transactionDetails(payURefundTransactionDetails).message(payUPaymentRefundResponse.getMessage()).status(payUPaymentRefundResponse.getStatus()).build());
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private MultiValueMap<String, String> buildPayUInfoRequest (String client, String command, String var1, String... vars) {
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

    private String generateHashForPayUApi (String payUMerchantKey, String payUSalt, String command, String var1) {
        String builder = payUMerchantKey + PIPE_SEPARATOR +
                command +
                PIPE_SEPARATOR +
                var1 +
                PIPE_SEPARATOR +
                payUSalt;
        return EncryptionUtils.generateSHA512Hash(builder);
    }

    private <T> T getInfoFromPayU (MultiValueMap<String, String> request, TypeReference<T> target) {
        try {
            final String response = restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(payUInfoApiUrl)).body(request), String.class).getBody();
            return objectMapper.readValue(response, target);
        } catch (HttpStatusCodeException ex) {
            log.error(PAYU_API_FAILURE, ex.getResponseBodyAsString(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        }
    }

    private class ChargingTransactionReconciliationStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            syncChargingTransactionFromSource(transaction, Optional.empty());
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }

    private class RefundTransactionReconciliationStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            RefundTransactionReconciliationStatusRequest refundRequest = (RefundTransactionReconciliationStatusRequest) request;
            syncRefundTransactionFromSource(transaction, refundRequest.getExtTxnId());
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(PAYU_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(PAYU_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();

        }
    }

    private class Upi implements IPaymentChargingService<AbstractPayUUpiGatewayChargingResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractPayUUpiGatewayChargingResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public Upi() {
            chargeDelegate.put("SEAMLESS", new Seamless());
            chargeDelegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public AbstractPayUUpiGatewayChargingResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class NonSeamless implements IPaymentChargingService<PayUUpiCollectGatewayChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUUpiCollectGatewayChargingResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return PayUUpiCollectGatewayChargingResponse.builder().form(form).build();
            }

        }


        private class Seamless implements IPaymentChargingService<PayUUpiIntentGatewayChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUUpiIntentGatewayChargingResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                final PayUUpiIntentExternalChargingResponse externalResponse = initIntentUpiPayU(form);
                return PayUUpiIntentGatewayChargingResponse.builder().external(externalResponse).build();
            }

            private PayUUpiIntentExternalChargingResponse initIntentUpiPayU(Map<String, String> payUPayload) {
                try {
                    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
                    for (String key : payUPayload.keySet()) {
                        requestMap.add(key, payUPayload.get(key));
                    }
                    payUPayload.clear();
                    requestMap.add(PAYU_PG, "UPI");
                    requestMap.add(PAYU_TXN_S2S_FLOW, "4");
                    requestMap.add(PAYU_BANKCODE, "INTENT");
                    return restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(payUPaymentApiUrl)).body(requestMap), PayUUpiIntentExternalChargingResponse.class).getBody();
                } catch (Exception ex) {
                    throw new WynkRuntimeException(PAY015, ex);
                }
            }

        }

    }

    private class Card implements IPaymentChargingService<AbstractPayUCardGatewayChargingResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractPayUCardGatewayChargingResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public Card() {
            chargeDelegate.put("SEAMLESS", new Seamless());
            chargeDelegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public AbstractPayUCardGatewayChargingResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<PayUCardGatewaySeamlessChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUCardGatewaySeamlessChargingResponse charge(PayUChargingRequest<?> request) {
                return null;
            }
        }

        private class NonSeamless implements IPaymentChargingService<PayUCardGatewayNonSeamlessChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUCardGatewayNonSeamlessChargingResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return PayUCardGatewayNonSeamlessChargingResponse.builder().form(form).build();
            }
        }
    }

    private class NetBanking implements IPaymentChargingService<AbstractPayUNetBankingGatewayChargingResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractPayUNetBankingGatewayChargingResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public NetBanking() {
            chargeDelegate.put("SEAMLESS", new Seamless());
            chargeDelegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public AbstractPayUNetBankingGatewayChargingResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<PayUNetBankingGatewaySeamlessChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUNetBankingGatewaySeamlessChargingResponse charge(PayUChargingRequest<?> request) {
                return null;
            }
        }

        private class NonSeamless implements IPaymentChargingService<PayUNetBankingGatewayNonSeamlessChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUNetBankingGatewayNonSeamlessChargingResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return PayUNetBankingGatewayNonSeamlessChargingResponse.builder().form(form).build();
            }
        }
    }

    private class Wallet implements IPaymentChargingService<AbstractPayUWalletGatewayChargingResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractPayUWalletGatewayChargingResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public Wallet() {
            chargeDelegate.put("SEAMLESS", new Seamless());
            chargeDelegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public AbstractPayUWalletGatewayChargingResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<PayUWalletGatewaySeamlessChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUWalletGatewaySeamlessChargingResponse charge(PayUChargingRequest<?> request) {
                return null;
            }
        }

        private class NonSeamless implements IPaymentChargingService<PayUWalletGatewayNonSeamlessChargingResponse, PayUChargingRequest<?>> {

            @Override
            public PayUWalletGatewayNonSeamlessChargingResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return PayUWalletGatewayNonSeamlessChargingResponse.builder().form(form).build();
            }
        }
    }

}
