package in.wynk.payment.service.impl;


import in.wynk.common.dto.ICacheService;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.response.PaymentStatusWrapper;
import in.wynk.payment.dto.payu.PayUChargingRequest;
import in.wynk.payment.dto.payu.PaymentRequestType;
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
import in.wynk.payment.service.IPaymentChargingService;
import in.wynk.payment.service.IPaymentStatus;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.COLON;
import static in.wynk.payment.core.constant.BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.error.codes.core.constant.ErrorCodeConstants.FAIL001;
import static in.wynk.error.codes.core.constant.ErrorCodeConstants.FAIL002;
import static in.wynk.payment.dto.payu.PayUConstants.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PAYU_MERCHANT_PAYMENT_SERVICE + VERSION_2)
public class PayUPaymentGateway implements IPaymentChargingService<PayUGatewayChargingResponse, PayUChargingRequest<?>>, IPaymentStatus<PaymentStatusWrapper> {

    private final IErrorCodesCacheService errorCodesCacheServiceImpl;
    private final RestTemplate restTemplate;
    private final ICacheService<PaymentMethod, String> paymentMethodCachingService;
    private final Map<String, IPaymentChargingService<? extends PayUGatewayChargingResponse, PayUChargingRequest<?>>> chargeDelegate = new HashMap<>();

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;
    @Value("${payment.merchant.payu.api.payment}")
    private String payUPaymentApiUrl;
    @Value("${payment.encKey}")
    private String encryptionKey;

    public PayUPaymentGateway(@Qualifier(EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ICacheService<PaymentMethod, String> paymentMethodCachingService,IErrorCodesCacheService errorCodesCacheServiceImpl) {
        this.restTemplate = restTemplate;
        this.chargeDelegate.put("UPI", new Upi());
        this.chargeDelegate.put("CARD", new Card());
        this.chargeDelegate.put("WALLET", new Wallet());
        this.chargeDelegate.put("NET_BANKING", new NetBanking());
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.errorCodesCacheServiceImpl = errorCodesCacheServiceImpl;
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
    public PaymentStatusWrapper status (Transaction transaction) {
        TransactionStatus txnStatus = transaction.getStatus();
        PaymentStatusWrapper.PaymentStatusWrapperBuilder<?, ?> builder = PaymentStatusWrapper.builder().transaction(transaction).planId(transaction.getPlanId());
        if (EnumSet.of(TransactionStatus.FAILURE, TransactionStatus.FAILUREALREADYSUBSCRIBED).contains(txnStatus)) {
            builder.errorCode(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL001));
        } else if (txnStatus == TransactionStatus.INPROGRESS) {
            builder.errorCode(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL002));
        }
        return builder.build();
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
