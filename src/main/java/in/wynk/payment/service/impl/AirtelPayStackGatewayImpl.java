package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.*;
import in.wynk.payment.dto.aps.request.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.request.refund.ApsExternalPaymentRefundRequest;
import in.wynk.payment.dto.aps.request.sattlement.ApsSettlementRequest;
import in.wynk.payment.dto.aps.request.status.refund.ApsRefundStatusRequest;
import in.wynk.payment.dto.aps.request.verify.aps.ApsBinVerificationRequest;
import in.wynk.payment.dto.aps.response.charge.*;
import in.wynk.payment.dto.aps.response.refund.ApsExternalPaymentRefundStatusResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.aps.response.verify.ApsBinVerificationResponseData;
import in.wynk.payment.dto.aps.response.verify.ApsVpaVerificationData;
import in.wynk.payment.dto.aps.verify.ApsCardVerificationResponseWrapper;
import in.wynk.payment.dto.aps.verify.ApsVpaVerificationResponseWrapper;
import in.wynk.payment.dto.gateway.card.AbstractCoreCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractNonSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.CardHtmlTypeChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.AbstractCoreNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingChargingResponse;
import in.wynk.payment.dto.gateway.upi.*;
import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.dto.payu.PayUConstants;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.common.FreshCardDetails;
import in.wynk.payment.dto.request.common.SavedCardDetails;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.WYNK;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.apb.ApbConstants.*;

@Slf4j
@Service(PaymentConstants.AIRTEL_PAY_STACK)
public class AirtelPayStackGatewayImpl extends AbstractMerchantPaymentStatusService implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>,
        IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, PaymentGatewaySettlementRequest>,
        IMerchantVerificationServiceV2<AbstractPaymentInstrumentVerificationResponse, VerificationRequest>, IMerchantPaymentRefundService<ApsPaymentRefundResponse, ApsPaymentRefundRequest> /*,
       IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, IPreDebitNotificationServiceV2*/ {

    @Value("${payment.encKey}")
    private String encryptionKey;
    @Value("${aps.payment.encryption.key.path}")
    private String RSA_PUBLIC_KEY;
    @Value("${aps.payment.init.refund.api}")
    private String REFUND_ENDPOINT;
    @Value("${aps.payment.init.charge.api}")
    private String CHARGING_ENDPOINT;
    @Value("${aps.payment.init.charge.upi.api}")
    private String UPI_CHARGING_ENDPOINT;
    @Value("${aps.payment.init.settlement.api}")
    private String SETTLEMENT_ENDPOINT;
    @Value("${aps.payment.verify.bin.api}")
    private String BIN_VERIFY_ENDPOINT;
    @Value("${aps.payment.verify.vpa.api}")
    private String VPA_VERIFY_ENDPOINT;
    @Value("${aps.payment.option.api}")
    private String PAYMENT_OPTION_ENDPOINT;
    @Value("${aps.payment.saved.details.api}")
    private String SAVED_DETAILS_ENDPOINT;
    @Value("${aps.payment.refund.status.api}")
    private String REFUND_STATUS_ENDPOINT;
    @Value("${aps.payment.charge.status.api}")
    private String CHARGING_STATUS_ENDPOINT;
    @Value("${payment.polling.page}")
    private String CLIENT_POLLING_SCREEN_URL;
    @Value("${aps.payment.predebit.api}")
    private String PRE_DEBIT_API;
    @Value("${aps.payment.renewal.api}")
    private String SI_PAYMENT_API;

    private EncryptionUtils.RSA rsa;
    private final Gson gson;
    private final RestTemplate httpTemplate;
    private final ResourceLoader resourceLoader;
    private final PaymentCachingService cache;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMethodEligibilityVerification verification = new PaymentMethodEligibilityVerification();

    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;
    private ITransactionManagerService transactionManager;

    private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>> chargingDelegate = new HashMap<>();

    public AirtelPayStackGatewayImpl (Gson gson, @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, PaymentCachingService cache, IErrorCodesCacheService errorCache,
                                      ApplicationEventPublisher eventPublisher, ResourceLoader resourceLoader,
                                      PaymentMethodCachingService paymentMethodCachingService, ObjectMapper objectMapper, PaymentCachingService cachingService,
                                      IMerchantTransactionService merchantTransactionService, ITransactionManagerService transactionManager) {
        super(cache, errorCache);
        this.gson = gson;
        this.cache = cache;
        this.httpTemplate = httpTemplate;
        this.eventPublisher = eventPublisher;
        this.resourceLoader = resourceLoader;
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.objectMapper = objectMapper;
        this.cachingService = cachingService;
        this.merchantTransactionService = merchantTransactionService;
        this.transactionManager = transactionManager;
        chargingDelegate.put(PaymentConstants.UPI, new UpiCharging());
        chargingDelegate.put(PaymentConstants.CARD, new CardCharging());
        chargingDelegate.put(PaymentConstants.NET_BANKING, new NetBankingCharging());
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        final Resource resource = this.resourceLoader.getResource(RSA_PUBLIC_KEY);
        rsa = new EncryptionUtils.RSA(EncryptionUtils.RSA.KeyReader.readPublicKey(resource.getFile()));
        this.httpTemplate.getInterceptors().add((request, body, execution) -> {
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
            final String username = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
            final String password = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
            final String token = AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
            request.getHeaders().add(CHANNEL_ID, AUTH_TYPE_WEB_UNAUTH);
            request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            return execution.execute(request, body);
        });
    }

    @Override
    public AbstractPaymentInstrumentVerificationResponse doVerify (VerificationRequest request) {
        return verification.doVerify(request);
    }

    @Override
    public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
        final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
        return chargingDelegate.get(method.getGroup().toUpperCase()).charge(request);
    }

    private class PaymentMethodEligibilityVerification implements IMerchantVerificationServiceV2<AbstractPaymentInstrumentVerificationResponse, VerificationRequest> {
        private final Map<VerificationType, IMerchantVerificationServiceV2<AbstractPaymentInstrumentVerificationResponse, VerificationRequest>> verificationDelegate = new HashMap<>();

        public PaymentMethodEligibilityVerification () {
            verificationDelegate.put(VerificationType.VPA, new VpaVerification());
            verificationDelegate.put(VerificationType.BIN, new BinVerification());//Card verification
        }

        @Override
        public AbstractPaymentInstrumentVerificationResponse doVerify (VerificationRequest request) {
            return verificationDelegate.get(request.getVerificationType()).doVerify(request);
        }

        private class BinVerification implements IMerchantVerificationServiceV2<AbstractPaymentInstrumentVerificationResponse, VerificationRequest> {
            @Override
            public BinVerificationResponse doVerify (VerificationRequest request) {
                final ApsBinVerificationRequest binRequest = ApsBinVerificationRequest.builder().cardBin(request.getVerifyValue()).build();
                final RequestEntity<ApsBinVerificationRequest> entity = new RequestEntity<>(binRequest, new HttpHeaders(), HttpMethod.POST, URI.create(BIN_VERIFY_ENDPOINT));
                try {
                    final ResponseEntity<ApsCardVerificationResponseWrapper<ApsBinVerificationResponseData>> response =
                            httpTemplate.exchange(entity, new ParameterizedTypeReference<ApsCardVerificationResponseWrapper<ApsBinVerificationResponseData>>() {
                            });
                    final ApsCardVerificationResponseWrapper<ApsBinVerificationResponseData> wrapper = response.getBody();
                    if (Objects.nonNull(wrapper) && wrapper.isResult()) {
                        final ApsBinVerificationResponseData body = wrapper.getData();
                        return BinVerificationResponse.builder().cardCategory(body.getCardCategory()).cardType(body.getCardNetwork()).issuingBank(body.getBankCode())
                                .isAutoRenewSupported(body.isAutoPayEnable()).isDomestic(body.isDomestic()).build();
                    }
                } catch (Exception e) {
                    log.error(APS_BIN_VERIFICATION, "Bin Verification Request failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY039);
            }
        }

        private class VpaVerification implements IMerchantVerificationServiceV2<AbstractPaymentInstrumentVerificationResponse, VerificationRequest> {
            @Override
            public AbstractPaymentInstrumentVerificationResponse doVerify (VerificationRequest request) {
                String userVpa = request.getVerifyValue();
                String lob = WYNK;
                final URI uri = httpTemplate.getUriTemplateHandler().expand(VPA_VERIFY_ENDPOINT, userVpa, lob);
                try {
                    final ResponseEntity<ApsVpaVerificationResponseWrapper<ApsVpaVerificationData>> wrapper =
                            httpTemplate.exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<ApsVpaVerificationResponseWrapper<ApsVpaVerificationData>>() {
                            });
                    final ApsVpaVerificationResponseWrapper<ApsVpaVerificationData> response = wrapper.getBody();

                    if (Objects.nonNull(response) && response.isResult()) {
                        ApsVpaVerificationData body = response.getData();
                        return VpaVerificationResponse.builder().verifyValue(request.getVerifyValue()).verificationType(VerificationType.VPA).isAutoRenewSupported(response.isAutoPayHandleValid())
                                .vpa(response.getVpa()).payeeAccountName(response.getPayeeAccountName())
                                .isValid(response.isVpaValid()).transactionStatus(APS_VERIFY_TRANSACTION_SUCCESS.equals(body.getStatus()) ? TransactionStatus.SUCCESS : TransactionStatus.FAILURE)
                                .errorMessage(response.getErrorMessage())
                                .build();
                    }
                } catch (Exception e) {
                    log.error(APS_VPA_VERIFICATION, "Vpa verification failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY039);
            }
        }
    }

    private class UpiCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {
        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

        public UpiCharging () {
            upiDelegate.put(PaymentConstants.SEAMLESS, new UpiSeamlessCharging());
            upiDelegate.put(PaymentConstants.NON_SEAMLESS, new UpiNonSeamlessCharging());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
            String flowType = paymentDetails.getUpiDetails().isSeamless() ? PaymentConstants.SEAMLESS : PaymentConstants.NON_SEAMLESS;
            return upiDelegate.get(flowType).charge(request);
        }

        private class UpiNonSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2> {
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractNonSeamlessUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public UpiNonSeamlessCharging () {
                upiDelegate.put(PaymentConstants.COLLECT, new UpiCollectCharging());
            }

            @Override
            public AbstractCoreUpiChargingResponse charge (AbstractChargingRequestV2 request) {
                return upiDelegate.get(PaymentConstants.COLLECT).charge(request);
            }

            /**
             * UPI Collect redirect flow
             * If autoRenew in request is true means it was verified by other API call
             */
            private class UpiCollectCharging implements IMerchantPaymentChargingServiceV2<AbstractNonSeamlessUpiChargingResponse, AbstractChargingRequestV2> {

                @Override
                public UpiCollectChargingResponse charge (AbstractChargingRequestV2 request) {
                    final Transaction transaction = TransactionContext.get();
                    final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                    final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();

                    final HttpHeaders headers = new HttpHeaders();
                    ApsExternalChargingRequest.ApsExternalChargingRequestBuilder<CollectUpiPaymentInfo> apsChargingRequestBuilder =
                            ApsExternalChargingRequest.<CollectUpiPaymentInfo>builder().orderId(transaction.getIdStr()).userInfo(userInfo)
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build());
                    CollectUpiPaymentInfo.CollectUpiPaymentInfoBuilder<?, ?> paymentInfoBuilder =
                            CollectUpiPaymentInfo.builder().vpa(paymentDetails.getUpiDetails().getVpa()).paymentAmount(transaction.getAmount());

                    //if auto-renew true means user's mandate should be registered. Update fields in request for autoRenew
                    if (paymentDetails.isAutoRenew()) {
                        //iv-user is mandatory header
                        headers.set(IV_USER, request.getUserDetails().getMsisdn());
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.HOUR, 24);
                        Date today = cal.getTime();
                        cal.add(Calendar.YEAR, 10); // 10 yrs from now
                        Date next10Year = cal.getTime();
                        paymentInfoBuilder.mandateAmount(transaction.getAmount()).paymentStartDate(today.toString()).paymentEndDate(next10Year.toString());
                        apsChargingRequestBuilder.signature(generateSignature()).isPennyDropTxn(false);
                    }

                    final ApsExternalChargingRequest<CollectUpiPaymentInfo> payRequest = apsChargingRequestBuilder.paymentInfo(paymentInfoBuilder.build()).build();
                    final RequestEntity<ApsExternalChargingRequest<CollectUpiPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(UPI_CHARGING_ENDPOINT));
                    try {
                        final ResponseEntity<ApsApiResponseWrapper<ApsUpiCollectChargingResponse>> response = exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsUpiCollectChargingResponse>>() {});
                        if (Objects.nonNull(response.getBody())) {
                            if (response.getBody().isResult()) {
                                return UpiCollectChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).url(CLIENT_POLLING_SCREEN_URL).build();
                            } else {
                                throw new WynkRuntimeException(PaymentErrorType.PAY038, response.getBody().getErrorMessage());
                            }
                        }

                    } catch (Exception e) {
                        log.error(APS_CHARGING_FAILURE, "unable to call APS external service due to ", e);
                        throw new WynkRuntimeException(PaymentErrorType.PAY038, e);
                    }
                    throw new WynkRuntimeException(PaymentErrorType.PAY024);
                }
            }
        }

        private class UpiSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2> {
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractSeamlessUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public UpiSeamlessCharging () {
                upiDelegate.put(PaymentConstants.INTENT, new UpiIntentCharging());
                upiDelegate.put(PaymentConstants.COLLECT_IN_APP, new UpiCollectInAppCharging());
            }

            @Override
            public AbstractSeamlessUpiChargingResponse charge (AbstractChargingRequestV2 request) {
                UpiPaymentDetails upiPaymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                String flow = PaymentConstants.INTENT;
                if (!upiPaymentDetails.getUpiDetails().isIntent()) {
                    flow = PaymentConstants.COLLECT_IN_APP;
                }
                return upiDelegate.get(flow).charge(request);
            }

            private class UpiCollectInAppCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessUpiChargingResponse, AbstractChargingRequestV2> {
                @Override
                public UpiCollectInAppChargingResponse charge (AbstractChargingRequestV2 request) {
                    //TODO: implement upi collect in app flow
                    return UpiCollectInAppChargingResponse.builder().build();
                }
            }

            private class UpiIntentCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessUpiChargingResponse, AbstractChargingRequestV2> {

                @Override
                @SneakyThrows
                public UpiIntentChargingResponse charge (AbstractChargingRequestV2 request) {
                    final Transaction transaction = TransactionContext.get();
                    final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                    final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
                    final String payAppName = (String) method.getMeta().get(PaymentConstants.APP_NAME);
                    final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final IntentUpiPaymentInfo upiIntentDetails =
                            IntentUpiPaymentInfo.builder().upiDetails(IntentUpiPaymentInfo.UpiDetails.builder().appName(payAppName).build()).paymentAmount(transaction.getAmount()).build();
                    //TODO: Update request for UPI Intent once done from APS and call mandate creation
                    final ApsExternalChargingRequest<IntentUpiPaymentInfo> payRequest =
                            ApsExternalChargingRequest.<IntentUpiPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiIntentDetails)
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                    final HttpHeaders headers = new HttpHeaders();
                    final RequestEntity<ApsExternalChargingRequest<IntentUpiPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(UPI_CHARGING_ENDPOINT));

                    final ResponseEntity<ApsApiResponseWrapper<ApsUpiIntentChargingChargingResponse>> response =
                            exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsUpiIntentChargingChargingResponse>>() {
                            });

                    if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                        Map<String, String> map = Arrays.stream(response.getBody().getData().getUpiLink().split("&")).map(s -> s.split("=", 2)).filter(p -> StringUtils.isNotBlank(p[1]))
                                .collect(Collectors.toMap(x -> x[0], x -> x[1]));
                        PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
                        String offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();

                        //if mandate created, means transaction type is SUBSCRIBE else PURCHASE
                        return UpiIntentChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(PaymentEvent.SUBSCRIBE.getValue())
                                .pa(map.get(PA)).pn(map.getOrDefault(PN, WYNK_LIMITED)).tr(map.get(TR)).am(map.get(AM))
                                .cu(map.getOrDefault(CU, CURRENCY_INR)).tn(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get(TN)).mc(PayUConstants.WYNK_UPI_MERCHANT_CODE)
                                .build();

                    }
                    throw new WynkRuntimeException(PaymentErrorType.PAY024);
                }
            }
        }
    }

    //This should be updated as per signatutre generation rule from APS
    private String generateSignature () {
        return "AAAADPKS85n2ZNckSP82K8SH1HqFufrnEW7JDvotTMgF76pZDPKBzRVv8i0F CURjJh4AdSxh3-gIXOlF8gS5Tw==";
    }

    private class CardCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

        public CardCharging () {
            upiDelegate.put(PaymentConstants.INTENT, new CardSeamlessCharging());
            upiDelegate.put(PaymentConstants.COLLECT_IN_APP, new CardNonSeamlessCharging());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            return null;
        }

        private class CardSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2> {
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public CardSeamlessCharging () {
                upiDelegate.put(PaymentConstants.INTENT, new CardOtpLessCharging());
                upiDelegate.put(PaymentConstants.COLLECT_IN_APP, new CardInAppCharging());
            }

            @Override
            public AbstractSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {
                return null;
            }

            private class CardOtpLessCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2> {

                @Override
                public AbstractSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {
                    return null;
                }
            }

            private class CardInAppCharging implements IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2> {

                @Override
                public AbstractSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {
                    return null;
                }
            }
        }

        private class CardNonSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2> {
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractNonSeamlessCardChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public CardNonSeamlessCharging () {
                upiDelegate.put(PaymentConstants.INTENT, new CardHtmlTypeCharging());//APS supports html type
            }

            @Override
            public AbstractNonSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {
                return null;
            }

            private class CardHtmlTypeCharging implements IMerchantPaymentChargingServiceV2<AbstractNonSeamlessCardChargingResponse, AbstractChargingRequestV2> {

                @Override
                public AbstractNonSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {

                    final Transaction transaction = TransactionContext.get();
                    final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
                    AbstractCardPaymentInfo.AbstractCardPaymentInfoBuilder<?, ?> abstractCardPaymentInfoBuilder = null;
                    try {
                        if (FRESH_CARD_TYPE.equals(paymentDetails.getCardDetails().getType())) {
                            final FreshCardDetails cardDetails = (FreshCardDetails) paymentDetails.getCardDetails();
                            final CardDetails credentials =
                                    CardDetails.builder().nameOnCard(((FreshCardDetails) paymentDetails.getCardDetails()).getCardHolderName()).cardNumber(cardDetails.getCardNumber())
                                            .expiryMonth(cardDetails.getExpiryInfo().getMonth()).expiryYear(cardDetails.getExpiryInfo().getYear()).cvv(cardDetails.getCardInfo().getCvv()).build();
                            final String encCardInfo = rsa.encrypt(gson.toJson(credentials));
                            abstractCardPaymentInfoBuilder =
                                    FreshCardPaymentInfo.builder().cardDetails(encCardInfo).bankCode(cardDetails.getCardInfo().getBankCode()).saveCard(cardDetails.isSaveCard())
                                            .paymentAmount(transaction.getAmount()).paymentMode(cardDetails.getCardInfo().getCategory());

                        } else {
                            final SavedCardDetails cardDetails = (SavedCardDetails) paymentDetails.getCardDetails();
                            final CardDetails credentials = CardDetails.builder().cvv(cardDetails.getCardInfo().getCvv()).cardToken(cardDetails.getCardToken()).build();
                            final String encCardInfo = rsa.encrypt(gson.toJson(credentials));
                            abstractCardPaymentInfoBuilder = SavedCardPaymentInfo.builder().savedCardDetails(encCardInfo).saveCard(cardDetails.isSaveCard()).paymentAmount(transaction.getAmount())
                                    .paymentMode(cardDetails.getCardInfo().getCategory());
                        }
                        assert abstractCardPaymentInfoBuilder != null;
                        final HttpHeaders headers = new HttpHeaders();
                        if (paymentDetails.isAutoRenew()) {
                            headers.set(IV_USER, request.getUserDetails().getMsisdn());
                            Calendar cal = Calendar.getInstance();
                            cal.add(Calendar.HOUR, 24);
                            Date today = cal.getTime();
                            cal.add(Calendar.YEAR, 10);
                            Date next10Year = cal.getTime();
                            abstractCardPaymentInfoBuilder.productCategory(POSTPAID).mandateAmount(transaction.getAmount()).paymentStartDate(today.toString()).paymentEndDate(next10Year.toString());
                        }
                        final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                        final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                        ApsExternalChargingRequest<?> payRequest = ApsExternalChargingRequest.builder().userInfo(userInfo).orderId(transaction.getIdStr())
                                .paymentInfo(abstractCardPaymentInfoBuilder.build()).channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                        RequestEntity<ApsExternalChargingRequest<?>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
                        final ResponseEntity<ApsApiResponseWrapper<ApsCardChargingResponse>> response =
                                exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsCardChargingResponse>>() {
                                });
                        if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                            final ApsCardChargingResponse cardChargingResponse = response.getBody().getData();
                            return CardHtmlTypeChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType("SUBSCRIBE")
                                    .html(cardChargingResponse.getHtml()).build();
                        }
                        throw new WynkRuntimeException(PaymentErrorType.PAY038, Objects.requireNonNull(response.getBody()).getErrorMessage());
                    } catch (Exception e) {
                        throw new WynkRuntimeException(PaymentErrorType.PAY024, e);
                    }
                }
            }
        }
    }


    private class NetBankingCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

        public NetBankingCharging () {
            upiDelegate.put(PaymentConstants.NET_BANKING, new NetBanking());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            return null;
        }

        private class NetBanking implements IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2> {

            @Override
            public AbstractCoreNetBankingChargingResponse charge (AbstractChargingRequestV2 request) {
                final Transaction transaction = TransactionContext.get();
                final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
                final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                final NetBankingPaymentInfo netBankingInfo =
                        NetBankingPaymentInfo.builder().bankCode((String) method.getMeta().get(PaymentConstants.BANK_CODE)).paymentAmount(transaction.getAmount()).build();
                final ApsExternalChargingRequest<NetBankingPaymentInfo> payRequest =
                        ApsExternalChargingRequest.<NetBankingPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(netBankingInfo)
                                .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                final HttpHeaders headers = new HttpHeaders();
                final RequestEntity<ApsExternalChargingRequest<NetBankingPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
                final ResponseEntity<ApsApiResponseWrapper<ApsNetBankingChargingResponse>> response =
                        exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsNetBankingChargingResponse>>() {
                        });
                if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                    final ApsNetBankingChargingResponse chargingResponse = response.getBody().getData();

                    return NetBankingChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).html(chargingResponse.getHtml()).build();
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY024);
            }
        }
    }


    private <R extends AbstractApsExternalChargingResponse, T> ResponseEntity<ApsApiResponseWrapper<R>> exchange (RequestEntity<T> entity,
                                                                                                                  ParameterizedTypeReference<ApsApiResponseWrapper<R>> target) {
        try {
            return httpTemplate.exchange(entity, target);
        } catch (Exception e) {
            throw new WynkRuntimeException(PAY024, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status (AbstractTransactionReconciliationStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        if (request instanceof ChargingTransactionReconciliationStatusRequest) {
            syncChargingTransactionFromSource(transaction);
        } else if (request instanceof RefundTransactionReconciliationStatusRequest) {
            syncRefundTransactionFromSource(transaction, request.getExtTxnId());
        } else {
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            return WynkResponseEntity.<AbstractChargingStatusResponse>builder()
                    .data(ChargingStatusResponse.success(transaction.getIdStr(), cache.validTillDate(transaction.getPlanId()), transaction.getPlanId())).build();
        } else if (transaction.getStatus() == TransactionStatus.FAILURE) {
            return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(ChargingStatusResponse.failure(transaction.getIdStr(), transaction.getPlanId())).build();
        }
        throw new WynkRuntimeException(PAY025);
    }

    private void syncRefundTransactionFromSource (Transaction transaction, String refundId) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final ApsRefundStatusRequest refundStatusRequest = ApsRefundStatusRequest.builder().refundId(refundId).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsRefundStatusRequest> requestEntity = new RequestEntity<>(refundStatusRequest, headers, HttpMethod.POST, URI.create(REFUND_STATUS_ENDPOINT));
            final ResponseEntity<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>> response =
                    httpTemplate.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>>() {
                    });
            final ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse> wrapper = response.getBody();
            assert wrapper != null;
            if (!wrapper.isResult()) {
                throw new WynkRuntimeException("Unable to initiate Refund");
            }
            final ApsExternalPaymentRefundStatusResponse body = wrapper.getData();
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
            final ResponseEntity<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>> response =
                    httpTemplate.exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>>() {
                    });
            final ApsApiResponseWrapper<List<ApsChargeStatusResponse>> wrapper = response.getBody();
            assert wrapper != null;
            if (wrapper.isResult()) {
                final List<ApsChargeStatusResponse> body = wrapper.getData();
                final ApsChargeStatusResponse status = body.get(0);
                if (status.getPaymentStatus().equalsIgnoreCase("PAYMENT_SUCCESS")) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                } else if (status.getPaymentStatus().equalsIgnoreCase("PAYMENT_FAILED")) {
                    transaction.setStatus(TransactionStatus.FAILURE.getValue());
                }
                builder.request(status).response(status);
                builder.externalTransactionId(status.getPgId());
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

    @Override
    public DefaultPaymentSettlementResponse settle (PaymentGatewaySettlementRequest request) {
        final String settlementOrderId = UUIDs.random().toString();
        final Transaction transaction = TransactionContext.get();
        final PlanDTO purchasedPlan = cache.getPlan(transaction.getPlanId());
        final List<ApsSettlementRequest.OrderDetails> orderDetails = purchasedPlan.getActivationServiceIds().stream()
                .map(serviceId -> ApsSettlementRequest.OrderDetails.builder().serviceOrderId(request.getTid()).serviceId(serviceId)
                        .paymentDetails(ApsSettlementRequest.OrderDetails.PaymentDetails.builder().paymentAmount(Double.toString(transaction.getAmount())).build()).build())
                .collect(Collectors.toList());
        final ApsSettlementRequest settlementRequest = ApsSettlementRequest.builder().channel("DIGITAL_STORE").orderId(settlementOrderId)
                .paymentDetails(ApsSettlementRequest.PaymentDetails.builder().paymentTransactionId(request.getTid()).orderPaymentAmount(transaction.getAmount()).build())
                .serviceOrderDetails(orderDetails).build();
        final HttpHeaders headers = new HttpHeaders();
        final RequestEntity<ApsSettlementRequest> requestEntity = new RequestEntity<>(settlementRequest, headers, HttpMethod.POST, URI.create(SETTLEMENT_ENDPOINT));
        httpTemplate.exchange(requestEntity, String.class);
        return DefaultPaymentSettlementResponse.builder().referenceId(settlementOrderId).build();
    }

    @Override
    public WynkResponseEntity<ApsPaymentRefundResponse> refund (ApsPaymentRefundRequest request) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final Transaction refundTransaction = TransactionContext.get();
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        final WynkResponseEntity.WynkResponseEntityBuilder<ApsPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        final ApsPaymentRefundResponse.ApsPaymentRefundResponseBuilder<?, ?> refundResponseBuilder =
                ApsPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId())
                        .itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn())
                        .paymentEvent(refundTransaction.getType());
        try {
            final ApsExternalPaymentRefundRequest refundRequest =
                    ApsExternalPaymentRefundRequest.builder().refundAmount(String.valueOf(refundTransaction.getAmount())).pgId(request.getPgId()).postingId(refundTransaction.getIdStr()).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsExternalPaymentRefundRequest> requestEntity = new RequestEntity<>(refundRequest, headers, HttpMethod.POST, URI.create(REFUND_ENDPOINT));
            final ResponseEntity<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>> response =
                    httpTemplate.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>>() {
                    });
            final ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse> wrapper = response.getBody();
            assert wrapper != null;
            if (!wrapper.isResult()) {
                throw new WynkRuntimeException("Unable to initiate Refund");
            }
            final ApsExternalPaymentRefundStatusResponse body = wrapper.getData();
            mBuilder.request(refundRequest);
            mBuilder.response(body);
            mBuilder.externalTransactionId(body.getRefundId());
            refundResponseBuilder.requestId(body.getRefundId());
            if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (WynkRuntimeException ex) {
            final PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            responseBuilder.success(false).status(ex.getErrorType().getHttpResponseStatusCode())
                    .error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(mBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }
}
