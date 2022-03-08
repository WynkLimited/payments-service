package in.wynk.payment.gateway.payu.charge;

import com.fasterxml.jackson.core.type.TypeReference;
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
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.gateway.payu.common.PayUCommonGateway;
import in.wynk.payment.utils.PropertyResolverUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.dto.payu.PayUConstants.*;

public class PayUChargingGateway implements IPaymentCharging<AbstractChargingGatewayResponse, PayUChargingRequest<?>> {

    private final PayUCommonGateway common;
    private final Map<String, IPaymentCharging<? extends AbstractChargingGatewayResponse, PayUChargingRequest<?>>> delegate = new HashMap<>();

    public PayUChargingGateway(PayUCommonGateway common) {
        this.common = common;
        delegate.put("UPI", new Upi());
        delegate.put("CARD", new Card());
        delegate.put("WALLET", new Wallet());
        delegate.put("NET_BAKING", new NetBanking());
    }

    @Override
    public AbstractChargingGatewayResponse charge(PayUChargingRequest<?> request) {
        final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentId());
        return delegate.get(paymentMethod.getFlowType()).charge(request);
    }

    private class Upi implements IPaymentCharging<AbstractUpiChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentCharging<? extends AbstractUpiChargingGatewayResponse, PayUChargingRequest<?>>> delegate = new HashMap<>();

        public Upi() {
            delegate.put(SEAMLESS_FLOW, new Seamless());
            delegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractUpiChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentId());
            return delegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentCharging<SeamlessUpiChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessUpiChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                final PayUUpiIntentInitResponse res = initIntentUpiPayU(form);
                final PayUUpiIntentInitResponse.Result result = res.getResult();
                return SeamlessUpiChargingGatewayResponse.builder().payeeVpa(result.getMerchantVpa()).merchantOrderID(result.getPaymentId()).amountToBePaid(result.getAmount()).payeeDisplayName(result.getMerchantName()).build();
            }

            private PayUUpiIntentInitResponse initIntentUpiPayU(Map<String, String> payUPayload) {
                MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
                for (String key : payUPayload.keySet()) {
                    requestMap.add(key, payUPayload.get(key));
                }
                payUPayload.clear();
                requestMap.add(PAYU_PG, "UPI");
                requestMap.add(PAYU_TXN_S2S_FLOW, "4");
                requestMap.add(PAYU_BANKCODE, "INTENT");
                return common.exchange(common.PAYMENT_API, requestMap, new TypeReference<PayUUpiIntentInitResponse>() {});
            }
        }

        private class NonSeamless implements IPaymentCharging<NonSeamlessUpiChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessUpiChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessUpiChargingGatewayResponse.builder().form(form).build();
            }
        }

    }

    private class Card implements IPaymentCharging<AbstractCardChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentCharging<? extends AbstractCardChargingGatewayResponse, PayUChargingRequest<?>>> delegate = new HashMap<>();

        public Card() {
            delegate.put(SEAMLESS_FLOW, new Seamless());
            delegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractCardChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentId());
            return delegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentCharging<SeamlessCardChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessCardChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        private class NonSeamless implements IPaymentCharging<NonSeamlessCardChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessCardChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessCardChargingGatewayResponse.builder().form(form).build();
            }
        }
    }

    private class NetBanking implements IPaymentCharging<AbstractNetBankingChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentCharging<? extends AbstractNetBankingChargingGatewayResponse, PayUChargingRequest<?>>> delegate = new HashMap<>();

        public NetBanking() {
            delegate.put(SEAMLESS_FLOW, new Seamless());
            delegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractNetBankingChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentId());
            return delegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentCharging<SeamlessNetBankingChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessNetBankingChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        private class NonSeamless implements IPaymentCharging<NonSeamlessNetBankingChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessNetBankingChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessNetBankingChargingGatewayResponse.builder().form(form).build();
            }
        }
    }

    private class Wallet implements IPaymentCharging<AbstractWalletChargingGatewayResponse, PayUChargingRequest<?>> {

        private final Map<String, IPaymentCharging<? extends AbstractWalletChargingGatewayResponse, PayUChargingRequest<?>>> delegate = new HashMap<>();

        public Wallet() {
            delegate.put(SEAMLESS_FLOW, new Seamless());
            delegate.put(NON_SEAMLESS_FLOW, new NonSeamless());
        }

        @Override
        public AbstractWalletChargingGatewayResponse charge(PayUChargingRequest<?> request) {
            final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentId());
            return delegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class Seamless implements IPaymentCharging<SeamlessWalletChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public SeamlessWalletChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        private class NonSeamless implements IPaymentCharging<NonSeamlessWalletChargingGatewayResponse, PayUChargingRequest<?>> {

            @Override
            public NonSeamlessWalletChargingGatewayResponse charge(PayUChargingRequest<?> request) {
                final Map<String, String> form = getPayload(request);
                return NonSeamlessWalletChargingGatewayResponse.builder().form(form).build();
            }
        }
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


}
