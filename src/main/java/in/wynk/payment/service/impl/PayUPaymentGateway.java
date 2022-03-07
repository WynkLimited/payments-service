package in.wynk.payment.service.impl;

import in.wynk.common.dto.ICacheService;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.card.AbstractCardChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.card.NonSeamlessCardChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.card.SeamlessCardChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.netbanking.AbstractNetBankingChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.netbanking.NonSeamlessNetBankingChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.netbanking.SeamlessNetBankingChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.upi.AbstractUpiChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.upi.NonSeamlessUpiChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.upi.SeamlessUpiChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.wallet.AbstractWalletChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.wallet.NonSeamlessWalletChargingGatewayResponse;
import in.wynk.payment.dto.gateway.charge.wallet.SeamlessWalletChargingGatewayResponse;
import in.wynk.payment.dto.payu.PayUChargingRequest;
import in.wynk.payment.dto.payu.PaymentRequestType;
import in.wynk.payment.dto.response.payu.PayUUpiIntentInitResponse;
import in.wynk.payment.service.IPaymentChargingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.COLON;
import static in.wynk.payment.core.constant.BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
@Service(PAYU_MERCHANT_PAYMENT_SERVICE + VERSION_2)
public class PayUPaymentGateway implements IPaymentChargingService<AbstractChargingGatewayResponse, PayUChargingRequest<?>> {

    private final RestTemplate restTemplate;
    private final ICacheService<PaymentMethod, String> paymentMethodCachingService;
    private final Map<String, IPaymentChargingService<? extends AbstractChargingGatewayResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;
    @Value("${payment.merchant.payu.api.payment}")
    private String payUPaymentApiUrl;
    @Value("${payment.encKey}")
    private String encryptionKey;

    public PayUPaymentGateway(@Qualifier(EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ICacheService<PaymentMethod, String> paymentMethodCachingService) {
        this.restTemplate = restTemplate;
        this.chargeDelegate.put("UPI", new Upi());
        this.chargeDelegate.put("CARD", new Card());
        this.chargeDelegate.put("WALLET", new Wallet());
        this.chargeDelegate.put("NET_BANKING", new NetBanking());
        this.paymentMethodCachingService = paymentMethodCachingService;
    }

    @Override
    public AbstractChargingGatewayResponse charge(PayUChargingRequest<?> request) {
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

    private class Upi implements IPaymentChargingService<AbstractUpiChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractUpiChargingGatewayResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public Upi() {
            chargeDelegate.put(SEAMLESS_FLOW, new Seamless());
            chargeDelegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractUpiChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<SeamlessUpiChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessUpiChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                final PayUUpiIntentInitResponse res = initIntentUpiPayU(form);
                final PayUUpiIntentInitResponse.Result result =  res.getResult();
                return SeamlessUpiChargingGatewayResponse.builder().payeeVpa(result.getMerchantVpa()).merchantOrderID(result.getPaymentId()).amountToBePaid(result.getAmount()).payeeDisplayName(result.getMerchantName()).build();
            }

            private PayUUpiIntentInitResponse initIntentUpiPayU(Map<String, String> payUPayload) {
                try {
                    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
                    for (String key : payUPayload.keySet()) {
                        requestMap.add(key, payUPayload.get(key));
                    }
                    payUPayload.clear();
                    requestMap.add(PAYU_PG, "UPI");
                    requestMap.add(PAYU_TXN_S2S_FLOW, "4");
                    requestMap.add(PAYU_BANKCODE, "INTENT");
                    return restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(payUPaymentApiUrl)).body(requestMap), PayUUpiIntentInitResponse.class).getBody();
                } catch (Exception ex) {
                    throw new WynkRuntimeException(PAY015, ex);
                }
            }
        }

        private class NonSeamless implements IPaymentChargingService<NonSeamlessUpiChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessUpiChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessUpiChargingGatewayResponse.builder().form(form).build();
            }
        }

    }

    private class Card implements IPaymentChargingService<AbstractCardChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractCardChargingGatewayResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public Card() {
            chargeDelegate.put(SEAMLESS_FLOW, new Seamless());
            chargeDelegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractCardChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<SeamlessCardChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessCardChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        private class NonSeamless implements IPaymentChargingService<NonSeamlessCardChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessCardChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessCardChargingGatewayResponse.builder().form(form).build();
            }
        }
    }

    private class NetBanking implements IPaymentChargingService<AbstractNetBankingChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractNetBankingChargingGatewayResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public NetBanking() {
            chargeDelegate.put(SEAMLESS_FLOW, new Seamless());
            chargeDelegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractNetBankingChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<SeamlessNetBankingChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessNetBankingChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        private class NonSeamless implements IPaymentChargingService<NonSeamlessNetBankingChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessNetBankingChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessNetBankingChargingGatewayResponse.builder().form(form).build();
            }
        }
    }

    private class Wallet implements IPaymentChargingService<AbstractWalletChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentChargingService<? extends AbstractWalletChargingGatewayResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

        public Wallet() {
            chargeDelegate.put(SEAMLESS_FLOW, new Seamless());
            chargeDelegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractWalletChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = paymentMethodCachingService.get(request.getPaymentId());
            return chargeDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentChargingService<SeamlessWalletChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessWalletChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        private class NonSeamless implements IPaymentChargingService<NonSeamlessWalletChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessWalletChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessWalletChargingGatewayResponse.builder().form(form).build();
            }
        }
    }

}