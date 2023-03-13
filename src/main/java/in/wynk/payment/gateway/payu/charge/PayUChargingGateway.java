package in.wynk.payment.gateway.payu.charge;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.card.*;
import in.wynk.payment.dto.gateway.netbanking.AbstractCoreNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NonSeamlessNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.upi.*;
import in.wynk.payment.dto.payu.PayUConstants;
import in.wynk.payment.dto.payu.PayUUpiCollectResponse;
import in.wynk.payment.dto.payu.PaymentRequestType;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.dto.response.payu.PayUUpiIntentInitResponse;
import in.wynk.payment.gateway.payu.common.PayUCommonGateway;
import in.wynk.payment.service.IMerchantPaymentChargingServiceV2;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.core.constant.UpiConstants.*;
import static in.wynk.payment.core.constant.UpiConstants.TN;
import static in.wynk.payment.dto.apb.ApbConstants.CURRENCY_INR;
import static in.wynk.payment.dto.payu.PayUConstants.*;
import static in.wynk.payment.core.constant.CardConstants.CARD;
import static in.wynk.payment.core.constant.NetBankingConstants.NET_BANKING;

@Slf4j
@Service(PaymentConstants.PAYU_CHARGE)
public class PayUChargingGateway implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

    private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>> delegate = new HashMap<>();
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final PayUCommonGateway common;

    public PayUChargingGateway(PayUCommonGateway common, PaymentMethodCachingService paymentMethodCachingService) {
        this.common = common;
        this.paymentMethodCachingService = paymentMethodCachingService;
        delegate.put(UPI, new PayUUpiCharging());
        delegate.put(CARD, new PayUCardCharging());
        delegate.put(NET_BANKING, new PayUNetBankingCharging());
    }

    @Override
    public AbstractCoreChargingResponse charge(AbstractChargingRequestV2 request) {
        final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
        return delegate.get(method.getGroup().toUpperCase()).charge(request);
    }

    private class PayUUpiCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {
        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

        public PayUUpiCharging() {
            upiDelegate.put(SEAMLESS, new PayUUpiSeamlessCharging());
            upiDelegate.put(NON_SEAMLESS, new PayUUpiNonSeamlessCharging());
        }

        @Override
        public AbstractCoreUpiChargingResponse charge(AbstractChargingRequestV2 request) {
            String flowType = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getFlowType();
            return upiDelegate.get(flowType).charge(request);
        }

        private class PayUUpiSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2> {

            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractSeamlessUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public PayUUpiSeamlessCharging () {
                upiDelegate.put(INTENT, new PayUUpiIntentCharging());
                upiDelegate.put(COLLECT_IN_APP, new PayUUpiCollectInAppCharging());
            }

            @Override
            public AbstractSeamlessUpiChargingResponse charge (AbstractChargingRequestV2 request) {
                UpiPaymentDetails upiPaymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                String flow = INTENT;
                if (!upiPaymentDetails.getUpiDetails().isIntent()) {
                    flow = COLLECT_IN_APP;
                }
                return upiDelegate.get(flow).charge(request);
            }

            private class PayUUpiCollectInAppCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessUpiChargingResponse, AbstractChargingRequestV2> {
                @Override
                public UpiCollectInAppChargingResponse charge (AbstractChargingRequestV2 request) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY888);
                }
            }

            private class PayUUpiIntentCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessUpiChargingResponse, AbstractChargingRequestV2> {

                @Override
                @SneakyThrows
                public UpiIntentChargingResponse charge(AbstractChargingRequestV2 request) {
                    final Transaction transaction = TransactionContext.get();
                    final Map<String, String> form = getPayload(request);
                    final PayUUpiIntentInitResponse res = initIntentUpiPayU(form);
                    final PayUUpiIntentInitResponse.Result result = res.getResult();
                    if (Objects.nonNull(result) && Objects.nonNull(result.getIntentURIData())) {
                        Map<String, String> map = Arrays.stream(result.getIntentURIData().split(AND)).map(s -> s.split(EQUAL, 2)).filter(p -> StringUtils.isNotBlank(p[1]))
                                .collect(Collectors.toMap(x -> x[0], x -> x[1]));
                        PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
                        String offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();
                        return UpiIntentChargingResponse.builder().tid(transaction.getIdStr().replaceAll("-", "")).transactionStatus(transaction.getStatus()).transactionType(PaymentEvent.SUBSCRIBE.getValue())
                                .pa(map.get(PA)).pn(map.getOrDefault(PN, PaymentConstants.DEFAULT_PN)).tr(map.get(TR)).am(map.get(AM))
                                .cu(map.getOrDefault(CU, CURRENCY_INR)).tn(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get(TN)).mc(PayUConstants.WYNK_UPI_MERCHANT_CODE)
                                .build();
                    }
                    throw new WynkRuntimeException(PAY015);
                }
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

        private class PayUUpiNonSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2> {

            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractNonSeamlessUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public PayUUpiNonSeamlessCharging () {
                upiDelegate.put(COLLECT, new PayUUpiCollectCharging());
            }

            @Override
            public AbstractCoreUpiChargingResponse charge (AbstractChargingRequestV2 request) {
                return upiDelegate.get(COLLECT).charge(request);
            }

            private class PayUUpiCollectCharging implements IMerchantPaymentChargingServiceV2<AbstractNonSeamlessUpiChargingResponse, AbstractChargingRequestV2> {

                @Override
                public UpiCollectChargingResponse charge(AbstractChargingRequestV2 request) {
                    final Transaction transaction = TransactionContext.get();
                    final Map<String, String> form = getPayload(request);
                    final UpiPaymentDetails upiDetails = ((UpiPaymentDetails) request.getPaymentDetails());
                    form.put(PAYU_VPA, upiDetails.getUpiDetails().getVpa());
                    final PayUUpiCollectResponse res = initCollectUpiPayU(form);
                    final PayUUpiCollectResponse.CollectResult result = res.getResult();
                    if (Objects.nonNull(result) && Objects.nonNull(result.getOtpPostUrl())) {
                        return UpiCollectChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue())
                                .url(result.getOtpPostUrl()).build();
                    }
                    throw new WynkRuntimeException(PAY015);
                }
            }

            private PayUUpiCollectResponse initCollectUpiPayU(Map<String, String> payUPayload) {
                MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
                for (String key : payUPayload.keySet()) {
                    requestMap.add(key, payUPayload.get(key));
                }
                payUPayload.clear();
                requestMap.add(PAYU_PG, UPI);
                requestMap.add(PAYU_TXN_S2S_FLOW, "4");
                requestMap.add(PAYU_BANKCODE, UPI);
                return common.exchange(common.PAYMENT_API, requestMap, new TypeReference<PayUUpiCollectResponse>() {});
            }
        }
    }

    private class PayUCardCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2>> cardDelegate = new HashMap<>();

        public PayUCardCharging() {
            cardDelegate.put(SEAMLESS, new PayUCardSeamlessCharging());
            cardDelegate.put(NON_SEAMLESS, new PayUCardNonSeamlessCharging());
        }

        @Override
        public AbstractCoreCardChargingResponse charge(AbstractChargingRequestV2 request) {
            final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
            final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
            boolean inAppOtpSupport = (Objects.isNull(paymentDetails.getCardDetails().getInAppOtpSupport())) ? method.isInAppOtpSupport() : paymentDetails.getCardDetails().getInAppOtpSupport();
            boolean isOtpLessSupport = (Objects.isNull(paymentDetails.getCardDetails().getOtpLessSupport())) ? method.isOtpLessSupport() : paymentDetails.getCardDetails().getOtpLessSupport();
            String flowType = (inAppOtpSupport || isOtpLessSupport) ? SEAMLESS : NON_SEAMLESS;
            return cardDelegate.get(flowType).charge(request);
        }

        private class PayUCardSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2> {

            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2>> cardDelegate = new HashMap<>();

            public PayUCardSeamlessCharging() {
                cardDelegate.put(OTP_LESS, new PayUCardOtpLessCharging());
                cardDelegate.put(COLLECT_IN_APP, new PayUCardCollectInAppCharging());
            }

            @Override
            public AbstractSeamlessCardChargingResponse charge(AbstractChargingRequestV2 request) {
                final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
                String flow = COLLECT_IN_APP;
                if(Objects.nonNull(paymentDetails.getCardDetails().getOtpLessSupport()) && paymentDetails.getCardDetails().getOtpLessSupport()) {
                    flow = OTP_LESS;
                }
                return cardDelegate.get(flow).charge(request);
            }

            private class PayUCardOtpLessCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2> {
                @Override
                public OtpLessCardChargingResponse charge(AbstractChargingRequestV2 request) {
                    throw new WynkRuntimeException("Otp Less Card charging is not implemented");
                }
            }

            private class PayUCardCollectInAppCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2> {
                @Override
                public CardInAppChargingResponse charge(AbstractChargingRequestV2 request) {
                    throw new WynkRuntimeException("Collect In app charging is not implemented");
                }
            }
        }

        private class PayUCardNonSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2> {

            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractNonSeamlessCardChargingResponse, AbstractChargingRequestV2>> cardDelegate = new HashMap<>();

            public PayUCardNonSeamlessCharging() {
                cardDelegate.put(FORM_BASED, new PayUCardFormTypeCharging());
            }

            @Override
            public AbstractCoreCardChargingResponse charge(AbstractChargingRequestV2 request) {
                return cardDelegate.get(FORM_BASED).charge(request);
            }

            private class PayUCardFormTypeCharging implements IMerchantPaymentChargingServiceV2<AbstractNonSeamlessCardChargingResponse, AbstractChargingRequestV2> {

                @Override
                public CardKeyValueTypeChargingResponse charge(AbstractChargingRequestV2 request) {
                    final Transaction transaction = TransactionContext.get();
                    final Map<String, String> form = getPayload(request);
                    return CardKeyValueTypeChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).form(form).build();
                }
            }
        }
    }

    private class PayUNetBankingCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2>> nbDelegate = new HashMap<>();

        public PayUNetBankingCharging() {
            nbDelegate.put(SEAMLESS, new PayUNetBankingSeamlessCharging());
            nbDelegate.put(NON_SEAMLESS, new PayUNetBankingNonSeamlessCharging());
        }

        @Override
        public AbstractCoreNetBankingChargingResponse charge(AbstractChargingRequestV2 request) {
            final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentDetails().getPaymentId());
            return nbDelegate.get(paymentMethod.getFlowType()).charge(request);
        }

        private class PayUNetBankingSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2> {

            @Override
            public NetBankingChargingResponse charge(AbstractChargingRequestV2 request) {
                throw new WynkRuntimeException(PaymentErrorType.PAY888);
            }
        }

        private class PayUNetBankingNonSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2> {

            @Override
            public NonSeamlessNetBankingChargingResponse charge(AbstractChargingRequestV2 request) {
                final Transaction transaction = TransactionContext.get();
                final Map<String, String> form = getPayload(request);
                return NonSeamlessNetBankingChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).form(form).build();
            }
        }
    }

    /*private class PayUWalletCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IPaymentCharging<? extends AbstractWalletChargingGatewayResponse, PayUChargingRequest<?>>> delegate = new HashMap<>();

        public PayUWalletCharging () {
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
    }*/

    private Map<String, String> getPayload(AbstractChargingRequestV2 chargingRequest) {
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
        payload.put(PAYU_SUCCESS_URL, ((IChargingDetails) chargingRequest).getCallbackDetails().getCallbackUrl());
        payload.put(PAYU_FAILURE_URL, ((IChargingDetails) chargingRequest).getCallbackDetails().getCallbackUrl());
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
