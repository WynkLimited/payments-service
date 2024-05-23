package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.common.enums.BillingCycle;
import in.wynk.payment.common.utils.BillingUtils;
import in.wynk.payment.constant.FlowType;
import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.card.*;
import in.wynk.payment.dto.gateway.netbanking.AbstractCoreNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingKeyValueTypeResponse;
import in.wynk.payment.dto.gateway.upi.*;
import in.wynk.payment.dto.payu.PayUConstants;
import in.wynk.payment.dto.payu.PayUUpiCollectResponse;
import in.wynk.payment.dto.payu.PaymentRequestType;
import in.wynk.payment.dto.payu.SiDetails;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.common.FreshCardDetails;
import in.wynk.payment.dto.request.common.SavedCardDetails;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.response.payu.PayUUpiIntentInitResponse;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.constant.CardConstants.FRESH_CARD_TYPE;
import static in.wynk.payment.constant.FlowType.COLLECT;
import static in.wynk.payment.constant.FlowType.INTENT;
import static in.wynk.payment.constant.FlowType.UPI;
import static in.wynk.payment.constant.FlowType.*;
import static in.wynk.payment.constant.UpiConstants.ORG_ID;
import static in.wynk.payment.constant.UpiConstants.*;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
public class PayUChargingGatewayImpl implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

    public String PAYMENT_API;
    private final PayUCommonGateway common;
    private final PaymentMethodCachingService methodCache;
    private final Map<FlowType, IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>> delegate = new HashMap<>();

    public PayUChargingGatewayImpl(PayUCommonGateway common, PaymentMethodCachingService methodCache, String paymentApi) {
        this.common = common;
        this.PAYMENT_API = paymentApi;
        this.methodCache = methodCache;
        this.delegate.put(UPI, new PayUUpiCharging());
        this.delegate.put(FlowType.CARD, new PayUCardCharging());
        this.delegate.put(NET_BANKING, new PayUNetBankingCharging());
    }

    @Override
    public AbstractPaymentChargingResponse charge(AbstractPaymentChargingRequest request) {
        final PaymentMethod method = methodCache.get(request.getPaymentDetails().getPaymentId());
        return delegate.get(FlowType.valueOf(method.getGroup().toUpperCase())).charge(request);
    }

    private class PayUUpiCharging implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {
        private final Map<FlowType, IPaymentCharging<AbstractCoreUpiChargingResponse, AbstractPaymentChargingRequest>> upiDelegate = new HashMap<>();

        public PayUUpiCharging() {
            upiDelegate.put(SEAMLESS, new PayUUpiSeamlessCharging());
            upiDelegate.put(NON_SEAMLESS, new PayUUpiNonSeamlessCharging());
        }

        @Override
        public AbstractCoreUpiChargingResponse charge(AbstractPaymentChargingRequest request) {
            final String flowType = methodCache.get(request.getPaymentDetails().getPaymentId()).getFlowType();
            return upiDelegate.get(FlowType.valueOf(flowType)).charge(request);
        }

        private class PayUUpiSeamlessCharging implements IPaymentCharging<AbstractCoreUpiChargingResponse, AbstractPaymentChargingRequest> {

            private final Map<FlowType, IPaymentCharging<AbstractSeamlessUpiChargingResponse, AbstractPaymentChargingRequest>> upiDelegate = new HashMap<>();

            public PayUUpiSeamlessCharging() {
                upiDelegate.put(INTENT, new PayUUpiIntentCharging());
                upiDelegate.put(COLLECT_IN_APP, new PayUUpiCollectInAppCharging());
            }

            @Override
            public AbstractSeamlessUpiChargingResponse charge(AbstractPaymentChargingRequest request) {
                final UpiPaymentDetails upiPaymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                FlowType flow = INTENT;
                if (!upiPaymentDetails.getUpiDetails().isIntent()) {
                    flow = COLLECT_IN_APP;
                }
                return upiDelegate.get(flow).charge(request);
            }

            private class PayUUpiCollectInAppCharging implements IPaymentCharging<AbstractSeamlessUpiChargingResponse, AbstractPaymentChargingRequest> {
                @Override
                public UpiCollectInAppChargingResponse charge(AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    try {
                        final Map<String, String> form = PayUChargingGatewayImpl.this.buildPayUForm(request);
                        final UpiPaymentDetails upiDetails = ((UpiPaymentDetails) request.getPaymentDetails());
                        form.put(PAYU_VPA, upiDetails.getUpiDetails().getVpa());
                        init(UpiConstants.UPI, form, new TypeReference<PayUUpiCollectResponse>() {
                        });
                        return UpiCollectInAppChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).build();
                    } catch (Exception e) {
                        throw new WynkRuntimeException(PAY015, e);
                    }
                }
            }

            private class PayUUpiIntentCharging implements IPaymentCharging<AbstractSeamlessUpiChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public UpiIntentChargingResponse charge(AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    final Map<String, String> form = PayUChargingGatewayImpl.this.buildPayUForm(request);
                    final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                    final String vpa = paymentDetails.getUpiDetails().getVpa();
                    if (!StringUtils.isEmpty(vpa)) form.put(PAYU_VPA, paymentDetails.getUpiDetails().getVpa());
                    final PayUUpiIntentInitResponse res = init(UpiConstants.INTENT, form, new TypeReference<PayUUpiIntentInitResponse>() {
                    });
                    final PayUUpiIntentInitResponse.IntentResult result = res.getResult();
                    if (Objects.nonNull(result) && Objects.nonNull(result.getIntentURIData())) {
                        final Map<String, String> map = Arrays.stream(result.getIntentURIData().split(AND)).map(s -> s.split(EQUAL, 2)).filter(p -> StringUtils.isNotBlank(p[1]))
                                .collect(Collectors.toMap(x -> x[0], x -> x[1]));
                        final PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
                        String offerTitle;
                        if (transaction.getType() == PaymentEvent.POINT_PURCHASE) {
                            offerTitle = transaction.getItemId();
                        } else {
                            offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();
                        }
                        return UpiIntentChargingResponse.builder()
                                .mn(map.get(MN))
                                .rev(map.get(REV))
                                .mode(map.get(MODE))
                                .recur(map.get(RECUR))
                                .block(map.get(BLOCK))
                                .orgId(map.get(ORG_ID))
                                .mc(PAYU_MERCHANT_CODE)
                                .amRule(map.get(AM_RULE))
                                .purpose(map.get(PURPOSE))
                                .txnType(map.get(TXN_TYPE))
                                .pa(result.getMerchantVpa())
                                .tid(transaction.getIdStr())
                                .recurType(map.get(RECUR_TYPE))
                                .pn(PaymentConstants.DEFAULT_PN)
                                .recurValue(map.get(RECUR_VALUE))
                                .validityEnd(map.get(VALIDITY_END))
                                .validityStart(map.get(VALIDITY_START))
                                .cu(map.getOrDefault(CU, PaymentConstants.CURRENCY_INR))
                                .transactionStatus(transaction.getStatus())
                                .tr(result.getPaymentId()).am(map.get(AM))
                                .transactionType(transaction.getType().getValue())
                                .tn(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get(TN)).mc(PayUConstants.PAYU_MERCHANT_CODE)
                                .build();
                    }
                    throw new WynkRuntimeException(PAY015);
                }
            }

            private <T> T init(String bankCode, Map<String, String> payUPayload, TypeReference<T> target) {
                final MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
                for (String key : payUPayload.keySet()) {
                    requestMap.add(key, payUPayload.get(key));
                }
                payUPayload.clear();
                requestMap.add(PAYU_PG, UpiConstants.UPI);
                requestMap.add(PAYU_TXN_S2S_FLOW, PAYU_TXN_S2S_FLOW_VALUE);
                requestMap.add(PAYU_BANKCODE, bankCode);
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                return common.exchange(common.PAYMENT_API, requestMap, headers, target);
            }
        }

        private class PayUUpiNonSeamlessCharging implements IPaymentCharging<AbstractCoreUpiChargingResponse, AbstractPaymentChargingRequest> {

            private final Map<FlowType, IPaymentCharging<AbstractNonSeamlessUpiChargingResponse, AbstractPaymentChargingRequest>> upiDelegate = new HashMap<>();

            public PayUUpiNonSeamlessCharging() {
                upiDelegate.put(COLLECT, new PayUUpiCollectCharging());
            }

            @Override
            public AbstractCoreUpiChargingResponse charge(AbstractPaymentChargingRequest request) {
                return upiDelegate.get(COLLECT).charge(request);
            }

            private class PayUUpiCollectCharging implements IPaymentCharging<AbstractNonSeamlessUpiChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public UpiCollectChargingResponse charge(AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    try {
                        final Map<String, String> form = PayUChargingGatewayImpl.this.buildPayUForm(request);
                        final UpiPaymentDetails upiDetails = ((UpiPaymentDetails) request.getPaymentDetails());
                        form.put(PAYU_VPA, upiDetails.getUpiDetails().getVpa());
                        return UpiCollectChargingResponse.builder().form(form).tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).build();
                    } catch (Exception e) {
                        throw new WynkRuntimeException(PAY015, e);
                    }
                }
            }
        }
    }

    private class PayUCardCharging implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

        private final Map<FlowType, IPaymentCharging<AbstractCoreCardChargingResponse, AbstractPaymentChargingRequest>> cardDelegate = new HashMap<>();

        public PayUCardCharging() {
            cardDelegate.put(SEAMLESS, new PayUCardSeamlessCharging());
            cardDelegate.put(NON_SEAMLESS, new PayUCardNonSeamlessCharging());
        }

        @Override
        public AbstractCoreCardChargingResponse charge(AbstractPaymentChargingRequest request) {
            final PaymentMethod method = methodCache.get(request.getPaymentDetails().getPaymentId());
            final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
            final boolean inAppOtpSupport = (Objects.isNull(paymentDetails.getCardDetails().getInAppOtpSupport())) ? method.isInAppOtpSupport() : paymentDetails.getCardDetails().getInAppOtpSupport();
            final boolean isOtpLessSupport = (Objects.isNull(paymentDetails.getCardDetails().getOtpLessSupport())) ? method.isOtpLessSupport() : paymentDetails.getCardDetails().getOtpLessSupport();
            final FlowType flowType = (inAppOtpSupport || isOtpLessSupport) ? SEAMLESS : NON_SEAMLESS;
            return cardDelegate.get(flowType).charge(request);
        }

        private class PayUCardSeamlessCharging implements IPaymentCharging<AbstractCoreCardChargingResponse, AbstractPaymentChargingRequest> {

            private final Map<FlowType, IPaymentCharging<AbstractSeamlessCardChargingResponse, AbstractPaymentChargingRequest>> cardDelegate = new HashMap<>();

            public PayUCardSeamlessCharging() {
                cardDelegate.put(OTP_LESS, new PayUCardOtpLessCharging());
                cardDelegate.put(COLLECT_IN_APP, new PayUCardCollectInAppCharging());
            }

            @Override
            public AbstractSeamlessCardChargingResponse charge(AbstractPaymentChargingRequest request) {
                final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
                FlowType flow = COLLECT_IN_APP;
                if (Objects.nonNull(paymentDetails.getCardDetails().getOtpLessSupport()) && paymentDetails.getCardDetails().getOtpLessSupport()) {
                    flow = OTP_LESS;
                }
                return cardDelegate.get(flow).charge(request);
            }

            private class PayUCardOtpLessCharging implements IPaymentCharging<AbstractSeamlessCardChargingResponse, AbstractPaymentChargingRequest> {
                @Override
                public OtpLessCardChargingResponse charge(AbstractPaymentChargingRequest request) {
                    throw new WynkRuntimeException("Otp Less Card charging is not implemented");
                }
            }

            private class PayUCardCollectInAppCharging implements IPaymentCharging<AbstractSeamlessCardChargingResponse, AbstractPaymentChargingRequest> {
                @Override
                public CardInAppChargingResponse charge(AbstractPaymentChargingRequest request) {
                    throw new WynkRuntimeException("Collect In app charging is not implemented");
                }
            }
        }

        private class PayUCardNonSeamlessCharging implements IPaymentCharging<AbstractCoreCardChargingResponse, AbstractPaymentChargingRequest> {

            private final Map<FlowType, IPaymentCharging<AbstractNonSeamlessCardChargingResponse, AbstractPaymentChargingRequest>> cardDelegate = new HashMap<>();

            public PayUCardNonSeamlessCharging() {
                cardDelegate.put(FORM_BASED, new PayUCardFormTypeCharging());
            }

            @Override
            public AbstractCoreCardChargingResponse charge(AbstractPaymentChargingRequest request) {
                return cardDelegate.get(FORM_BASED).charge(request);
            }

            private class PayUCardFormTypeCharging implements IPaymentCharging<AbstractNonSeamlessCardChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public CardKeyValueTypeChargingResponse charge(AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    final Map<String, String> form = PayUChargingGatewayImpl.this.buildPayUForm(request);
                    final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
                    final String storeCard = (paymentDetails.getCardDetails().isSaveCard()) ? "1" : "0";
                    form.put(PAYU_STORE_CARD, storeCard);
                    form.put(PAYU_BANKCODE, paymentDetails.getCardDetails().getCardInfo().getBankCode());
                    if (FRESH_CARD_TYPE.equals(paymentDetails.getCardDetails().getType())) {
                        FreshCardDetails cardDetails = (FreshCardDetails) paymentDetails.getCardDetails();
                        form.put(PAYU_CARD_EXP_MON, cardDetails.getExpiryInfo().getMonth());
                        form.put(PAYU_CARD_EXP_YEAR, cardDetails.getExpiryInfo().getYear());
                        form.put(PAYU_CARD_NUM, cardDetails.getCardNumber());
                        form.put(PAYU_CARD_HOLDER_NAME, cardDetails.getCardHolderName());
                        form.put(PAYU_CARD_CVV, cardDetails.getCardInfo().getCvv());
                    } else {
                        SavedCardDetails cardDetails = (SavedCardDetails) paymentDetails.getCardDetails();
                        form.put(PAYU_CARD_TOKEN, cardDetails.getCardToken());
                        form.put(PAYU_CARD_CVV, cardDetails.getCardInfo().getCvv());
                    }
                    form.put(PAYU_PG, paymentDetails.getCardDetails().getCardInfo().getCategory());
                    return CardKeyValueTypeChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).form(form).url(PAYMENT_API).build();
                }
            }
        }
    }

    private class PayUNetBankingCharging implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

        private final Map<FlowType, IPaymentCharging<AbstractCoreNetBankingChargingResponse, AbstractPaymentChargingRequest>> nbDelegate = new HashMap<>();

        public PayUNetBankingCharging() {
            nbDelegate.put(SEAMLESS, new PayUNetBankingSeamlessCharging());
            nbDelegate.put(NON_SEAMLESS, new PayUNetBankingNonSeamlessCharging());
        }

        @Override
        public AbstractCoreNetBankingChargingResponse charge(AbstractPaymentChargingRequest request) {
            final PaymentMethod paymentMethod = common.getCache().get(request.getPaymentDetails().getPaymentId());
            return nbDelegate.get(FlowType.valueOf(paymentMethod.getFlowType())).charge(request);
        }

        private class PayUNetBankingSeamlessCharging implements IPaymentCharging<AbstractCoreNetBankingChargingResponse, AbstractPaymentChargingRequest> {

            @Override
            public NetBankingChargingResponse charge(AbstractPaymentChargingRequest request) {
                throw new WynkRuntimeException(PaymentErrorType.PAY888);
            }
        }

        private class PayUNetBankingNonSeamlessCharging implements IPaymentCharging<AbstractCoreNetBankingChargingResponse, AbstractPaymentChargingRequest> {

            @Override
            public NetBankingKeyValueTypeResponse charge(AbstractPaymentChargingRequest request) {
                final Transaction transaction = TransactionContext.get();
                final Map<String, String> form = PayUChargingGatewayImpl.this.buildPayUForm(request);
                final PaymentMethod method = methodCache.get(request.getPaymentDetails().getPaymentId());
                form.put(PAYU_ENFORCE_PAY_METHOD, (String) method.getMeta().get(PaymentConstants.BANK_CODE));
                form.put(PAYU_BANKCODE, (String) method.getMeta().get(PaymentConstants.BANK_CODE));
                form.put(PAYU_ENFORCE_PAYMENT, NetBankingConstants.NETBANKING.toLowerCase());
                form.put(PAYU_PG, PAYU_PG_NET_BANKING_VALUE);
                return NetBankingKeyValueTypeResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).form(form).url(PAYMENT_API).build();
            }
        }
    }

    @SneakyThrows
    private Map<String, String> buildPayUForm(AbstractPaymentChargingRequest chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        final String id = chargingRequest.getProductDetails().getType().equals(BaseConstants.POINT) ? transaction.getItemId() : String.valueOf(transaction.getPlanId());
        double finalPlanAmount = transaction.getAmount();
        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), transaction.getPaymentChannel().getCode().toLowerCase(), MERCHANT_ID);
        String userCredentials = payUMerchantKey + COLON + uid;
        Map<String, String> payload = new HashMap<>();
        if (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION || transaction.getType() == PaymentEvent.MANDATE) {
            String reqType = PaymentRequestType.SUBSCRIBE.name();
            String udf1 = PAYU_SI_KEY.toUpperCase();
            Calendar cal = Calendar.getInstance();
            Date today = cal.getTime();
            cal.add(Calendar.YEAR, 5); // 5 yrs from now
            Date next5Year = cal.getTime();
            boolean isFreeTrial = (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION || transaction.getType() == PaymentEvent.MANDATE);
            BillingUtils billingUtils = new BillingUtils(1, BillingCycle.ADHOC);
            String siDetails = common.getMapper().writeValueAsString(new SiDetails(billingUtils.getBillingCycle(), billingUtils.getBillingInterval(), transaction.getMandateAmount(), today, next5Year));
            String checksumHash = calculateChecksum(transaction.getClientAlias(), transaction.getId(), udf1, email, uid, id, finalPlanAmount, siDetails);
            payload.put(PAYU_SI_KEY, "1");
            payload.put(PAYU_API_VERSION, "7");
            payload.put(PAYU_HASH, checksumHash);
            payload.put(PAYU_UDF1_PARAMETER, udf1);
            payload.put(PAYU_SI_DETAILS, siDetails);
            payload.put(PAYU_REQUEST_TYPE, reqType);
            payload.put(PAYU_FREE_TRIAL, isFreeTrial ? "1" : "0");
        } else {
            String udf1 = StringUtils.EMPTY;
            String reqType = PaymentRequestType.DEFAULT.name();
            String checksumHash = calculateChecksum(transaction.getClientAlias(), transaction.getId(), udf1, email, uid, id, finalPlanAmount);
            payload.put(PAYU_HASH, checksumHash);
            payload.put(PAYU_REQUEST_TYPE, reqType);
            payload.put(PAYU_UDF1_PARAMETER, udf1);
        }
        // Mandatory according to document
        payload.put(PAYU_MERCHANT_KEY, payUMerchantKey);
        payload.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString());
        payload.put(PAYU_TRANSACTION_AMOUNT, String.valueOf(finalPlanAmount));
        payload.put(PAYU_PRODUCT_INFO, id);
        payload.put(PAYU_CUSTOMER_FIRSTNAME, uid);
        payload.put(PAYU_CUSTOMER_EMAIL, email);
        payload.put(PAYU_CUSTOMER_MSISDN, msisdn);
        payload.put(PAYU_SUCCESS_URL, chargingRequest.getCallbackDetails().getCallbackUrl());
        payload.put(PAYU_FAILURE_URL, chargingRequest.getCallbackDetails().getCallbackUrl());
        // Not in document
        payload.put(PAYU_IS_FALLBACK_ATTEMPT, String.valueOf(false));
        payload.put(ERROR, PAYU_REDIRECT_MESSAGE);
        payload.put(PAYU_USER_CREDENTIALS, userCredentials);
        return payload;
    }

    private String calculateChecksum(String client, UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String rawChecksum = payUMerchantKey
                + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle
                + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + payUMerchantSecret;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

    private String calculateChecksum(String client, UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount, String siDetails) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String rawChecksum = payUMerchantKey + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + siDetails + PIPE_SEPARATOR + payUMerchantSecret;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

}
