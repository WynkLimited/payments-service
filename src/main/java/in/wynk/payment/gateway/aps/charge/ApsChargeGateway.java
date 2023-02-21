package in.wynk.payment.gateway.aps.charge;

import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.*;
import in.wynk.payment.dto.aps.request.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.response.charge.*;
import in.wynk.payment.dto.gateway.card.AbstractCoreCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractNonSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.CardHtmlTypeChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.AbstractCoreNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingChargingResponse;
import in.wynk.payment.dto.gateway.upi.*;
import in.wynk.payment.dto.payu.PayUConstants;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.common.FreshCardDetails;
import in.wynk.payment.dto.request.common.SavedCardDetails;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.service.IMerchantPaymentChargingServiceV2;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY024;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_FAILURE;
import static in.wynk.payment.core.constant.UpiConstants.*;
import static in.wynk.payment.dto.apb.ApbConstants.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.AIRTEL_PAY_STACK_CHARGE)
public class ApsChargeGateway implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

    private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>> chargingDelegate = new HashMap<>();
    private final PaymentMethodCachingService paymentMethodCachingService;
    private EncryptionUtils.RSA rsa;
    private final ResourceLoader resourceLoader;
    private final RestTemplate httpTemplate;
    private final Gson gson;

    @Value("${aps.payment.encryption.key.path}")
    private String RSA_PUBLIC_KEY;
    @Value("${aps.payment.init.charge.upi.api}")
    private String UPI_CHARGING_ENDPOINT;
    @Value("${aps.payment.init.charge.api}")
    private String CHARGING_ENDPOINT;
    @Value("${payment.polling.page}")
    private String CLIENT_POLLING_SCREEN_URL;

    public ApsChargeGateway (@Qualifier("apsHttpTemplate") RestTemplate httpTemplate, Gson gson, PaymentMethodCachingService paymentMethodCachingService, ResourceLoader resourceLoader) {
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.resourceLoader = resourceLoader;
        this.httpTemplate = httpTemplate;
        this.gson = gson;
        chargingDelegate.put(UPI, new UpiCharging());
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
    public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
        final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
        return chargingDelegate.get(method.getGroup().toUpperCase()).charge(request);
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
                upiDelegate.put(COLLECT, new UpiCollectCharging());
            }

            @Override
            public AbstractCoreUpiChargingResponse charge (AbstractChargingRequestV2 request) {
                return upiDelegate.get(COLLECT).charge(request);
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
                        apsChargingRequestBuilder.signature(generateSignature()).pennyDropTxn(false);
                    }

                    final ApsExternalChargingRequest<CollectUpiPaymentInfo> payRequest = apsChargingRequestBuilder.paymentInfo(paymentInfoBuilder.build()).build();
                    final RequestEntity<ApsExternalChargingRequest<CollectUpiPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(UPI_CHARGING_ENDPOINT));
                    try {
                        final ResponseEntity<ApsApiResponseWrapper<ApsUpiCollectChargingResponse>> response =
                                exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsUpiCollectChargingResponse>>() {
                                });
                        if (Objects.nonNull(response.getBody())) {
                            if (response.getBody().isResult()) {
                                return UpiCollectChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue())
                                        .url(CLIENT_POLLING_SCREEN_URL).build();
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
                upiDelegate.put(INTENT, new UpiIntentCharging());
                upiDelegate.put(COLLECT_IN_APP, new UpiCollectInAppCharging());
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
                                .pa(map.get(PA)).pn(map.getOrDefault(PN, PaymentConstants.WYNK_LIMITED)).tr(map.get(TR)).am(map.get(AM))
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
            upiDelegate.put(INTENT, new CardSeamlessCharging());
            upiDelegate.put(COLLECT_IN_APP, new CardNonSeamlessCharging());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            return null;
        }

        private class CardSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2> {
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

            public CardSeamlessCharging () {
                upiDelegate.put(INTENT, new CardOtpLessCharging());
                upiDelegate.put(COLLECT_IN_APP, new CardInAppCharging());
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
                upiDelegate.put(INTENT, new CardHtmlTypeCharging());//APS supports html type
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
}
