package in.wynk.payment.gateway.aps.service;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.*;
import in.wynk.payment.dto.aps.request.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.response.charge.ApsCardChargingResponse;
import in.wynk.payment.dto.aps.response.charge.ApsNetBankingChargingResponse;
import in.wynk.payment.dto.aps.response.charge.ApsUpiCollectChargingResponse;
import in.wynk.payment.dto.aps.response.charge.ApsUpiIntentChargingChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractCoreCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractNonSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.CardHtmlTypeChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.AbstractCoreNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingHtmlTypeResponse;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.CardConstants.CARD;
import static in.wynk.payment.core.constant.CardConstants.FRESH_CARD_TYPE;
import static in.wynk.payment.core.constant.NetBankingConstants.NET_BANKING;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.UpiConstants.*;
import static in.wynk.payment.dto.apb.ApbConstants.CURRENCY_INR;
import static in.wynk.payment.dto.apb.ApbConstants.LOB_AUTO_PAY_REGISTER;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsChargeGateway implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

    private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2>> chargingDelegate = new HashMap<>();
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final ApsCommonGateway common;
    private String UPI_CHARGING_ENDPOINT;
    private String CHARGING_ENDPOINT;

    public ApsChargeGateway (String upiChargeEndpoint, String commonChargeEndpoint, PaymentMethodCachingService paymentMethodCachingService, ApsCommonGateway common) {
        this.common = common;
        this.UPI_CHARGING_ENDPOINT = upiChargeEndpoint;
        this.CHARGING_ENDPOINT = commonChargeEndpoint;
        chargingDelegate.put(CARD, new CardCharging());
        chargingDelegate.put(UPI, new UpiCharging());
        chargingDelegate.put(NET_BANKING, new NetBankingCharging());
        this.paymentMethodCachingService = paymentMethodCachingService;
    }

    @Override
    public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
        final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
        return chargingDelegate.get(method.getGroup().toUpperCase()).charge(request);
    }

    private class UpiCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {
        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreUpiChargingResponse, AbstractChargingRequestV2>> upiDelegate = new HashMap<>();

        public UpiCharging () {
            upiDelegate.put(SEAMLESS, new UpiSeamlessCharging());
            upiDelegate.put(NON_SEAMLESS, new UpiNonSeamlessCharging());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            String flowType = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getFlowType();
            if (Objects.isNull(flowType)) {
                throw new WynkRuntimeException("flowType in configuration should not be null");
            }
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
                    throw new WynkRuntimeException("This flow is not supported in APS");
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
                    final Transaction transaction = TransactionContext.get();
                    final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                    final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                    ApsExternalChargingRequest.ApsExternalChargingRequestBuilder<CollectUpiPaymentInfo> apsChargingRequestBuilder =
                            ApsExternalChargingRequest.<CollectUpiPaymentInfo>builder().orderId(transaction.getIdStr()).userInfo(userInfo)
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build());
                    CollectUpiPaymentInfo.CollectUpiPaymentInfoBuilder<?, ?> paymentInfoBuilder =
                            CollectUpiPaymentInfo.builder().vpa(paymentDetails.getUpiDetails().getVpa()).paymentAmount(transaction.getAmount());
                    //if auto-renew true means user's mandate should be registered. Update fields in request for autoRenew
                    if (paymentDetails.isAutoRenew()) {
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.HOUR, 24);
                        Date today = cal.getTime();
                        cal.add(Calendar.YEAR, 10); // 10 yrs from now
                        Date next10Year = cal.getTime();
                        paymentInfoBuilder.lob(LOB_AUTO_PAY_REGISTER).mandateAmount(transaction.getAmount()).paymentStartDate(today.toString()).paymentEndDate(next10Year.toString());
                        apsChargingRequestBuilder.billPayment(false);
                    }
                    final ApsExternalChargingRequest<CollectUpiPaymentInfo> payRequest = apsChargingRequestBuilder.paymentInfo(paymentInfoBuilder.build()).build();
                    ApsUpiCollectChargingResponse response =
                            common.exchange(UPI_CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, ApsUpiCollectChargingResponse.class);
                    return UpiCollectInAppChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(updateTransactionType(response)).build();
                   // return UpiCollectInAppChargingResponse.builder().build();
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
                    final IntentUpiPaymentInfo upiIntentDetails = IntentUpiPaymentInfo.builder().upiApp(payAppName).paymentAmount(transaction.getAmount()).build();
                    //TODO: Update request for UPI Intent once done from APS and call mandate creation
                    final ApsExternalChargingRequest<IntentUpiPaymentInfo> payRequest =
                            ApsExternalChargingRequest.<IntentUpiPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiIntentDetails)
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                    ApsUpiIntentChargingChargingResponse apsUpiIntentChargingChargingResponse =
                            common.exchange(UPI_CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, ApsUpiIntentChargingChargingResponse.class);
                    Map<String, String> map = Arrays.stream(apsUpiIntentChargingChargingResponse.getUpiLink().split("\\?")[1].split("&")).map(s -> s.split("=", 2)).filter(p -> StringUtils.isNotBlank(p[1]))
                            .collect(Collectors.toMap(x -> x[0], x -> x[1]));
                    PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
                    String offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();

                    //if mandate created, means transaction type is SUBSCRIBE else PURCHASE
                    return UpiIntentChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(PaymentEvent.SUBSCRIBE.getValue())
                            .pa(map.get(PA)).pn(map.getOrDefault(PN, PaymentConstants.WYNK_LIMITED)).tr(map.get(TR)).am(map.get(AM))
                            .cu(map.getOrDefault(CU, CURRENCY_INR)).tn(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get(TN)).mc(PayUConstants.PAYU_MERCHANT_CODE)
                            .build();
                }
            }
        }
    }

    private String updateTransactionType (
            ApsUpiCollectChargingResponse response) {
        if (Objects.isNull(response.getPgSystemId())) {
            return PaymentEvent.PURCHASE.getValue();
        }
        return PaymentEvent.SUBSCRIBE.getValue();
    }

    private class CardCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2>> cardDelegate = new HashMap<>();

        public CardCharging () {
            cardDelegate.put(SEAMLESS, new CardSeamlessCharging());
            cardDelegate.put(NON_SEAMLESS, new CardNonSeamlessCharging());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            String flowType = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getFlowType();
            if (Objects.isNull(flowType)) {
                throw new WynkRuntimeException("flowType in configuration should not be null");
            }
            return cardDelegate.get(flowType).charge(request);
        }

        private class CardSeamlessCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreCardChargingResponse, AbstractChargingRequestV2> {
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractSeamlessCardChargingResponse, AbstractChargingRequestV2>> cardSeamlessDelegate = new HashMap<>();

            public CardSeamlessCharging () {
                cardSeamlessDelegate.put(INTENT, new CardOtpLessCharging());
                cardSeamlessDelegate.put(COLLECT_IN_APP, new CardInAppCharging());
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
            private final Map<String, IMerchantPaymentChargingServiceV2<AbstractNonSeamlessCardChargingResponse, AbstractChargingRequestV2>> cardNonSeamlessDelegate = new HashMap<>();

            public CardNonSeamlessCharging () {
                cardNonSeamlessDelegate.put(HTML, new CardHtmlTypeCharging());//APS supports html type
            }

            @Override
            public AbstractNonSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {
                return cardNonSeamlessDelegate.get(PaymentConstants.HTML).charge(request);
            }

            private class CardHtmlTypeCharging implements IMerchantPaymentChargingServiceV2<AbstractNonSeamlessCardChargingResponse, AbstractChargingRequestV2> {

                @Override
                public AbstractNonSeamlessCardChargingResponse charge (AbstractChargingRequestV2 request) {

                    final Transaction transaction = TransactionContext.get();
                    final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
                    String paymentMode = "DEBIT_CARD";
                    if (Objects.nonNull(paymentDetails.getCardDetails().getCardInfo().getCategory()) &&
                            (paymentDetails.getCardDetails().getCardInfo().getCategory().equals("CREDIT") || paymentDetails.getCardDetails().getCardInfo().getCategory().equals("creditcard"))) {
                        paymentMode = "CREDIT_CARD";
                    }
                    AbstractCardPaymentInfo.AbstractCardPaymentInfoBuilder<?, ?> abstractCardPaymentInfoBuilder = null;
                    try {
                        if (FRESH_CARD_TYPE.equals(paymentDetails.getCardDetails().getType())) {
                            final FreshCardDetails cardDetails = (FreshCardDetails) paymentDetails.getCardDetails();
                            final CardDetails credentials =
                                    CardDetails.builder().nameOnCard(((FreshCardDetails) paymentDetails.getCardDetails()).getCardHolderName()).cardNumber(cardDetails.getCardNumber())
                                            .expiryMonth(cardDetails.getExpiryInfo().getMonth()).expiryYear(cardDetails.getExpiryInfo().getYear()).cvv(cardDetails.getCardInfo().getCvv()).build();
                            final String encCardInfo = common.encryptCardData(credentials);
                            abstractCardPaymentInfoBuilder =
                                    FreshCardPaymentInfo.builder().cardDetails(encCardInfo).saveCard(cardDetails.isSaveCard())
                                            .favouriteCard(cardDetails.isSaveCard()).tokenizeConsent(cardDetails.isSaveCard())
                                            .paymentMode(paymentMode).paymentAmount(transaction.getAmount());
                        } else {
                            final SavedCardDetails cardDetails = (SavedCardDetails) paymentDetails.getCardDetails();
                            final CardDetails credentials = CardDetails.builder().cvv(cardDetails.getCardInfo().getCvv()).cardRefNumber(cardDetails.getCardToken()).build();
                            final String encCardInfo = common.encryptCardData(credentials);
                            abstractCardPaymentInfoBuilder =
                                    SavedCardPaymentInfo.builder().savedCardDetails(encCardInfo).paymentAmount(transaction.getAmount()).paymentMode(paymentMode);
                        }
                        assert abstractCardPaymentInfoBuilder != null;
                        if (paymentDetails.isAutoRenew()) {
                            Calendar cal = Calendar.getInstance();
                            cal.add(Calendar.HOUR, 24);
                            Date today = cal.getTime();
                            cal.add(Calendar.YEAR, 10);
                            Date next10Year = cal.getTime();
                            abstractCardPaymentInfoBuilder.lob(LOB_AUTO_PAY_REGISTER).productCategory(BaseConstants.WYNK).mandateAmount(transaction.getAmount()).paymentStartDate(today.toString())
                                    .paymentEndDate(next10Year.toString());
                        }
                        final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                        final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                        ApsExternalChargingRequest<?> payRequest =
                                ApsExternalChargingRequest.builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(abstractCardPaymentInfoBuilder.build())
                                        .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                        ApsCardChargingResponse cardChargingResponse =
                                common.exchange(CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, ApsCardChargingResponse.class);
                        return CardHtmlTypeChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType("SUBSCRIBE")
                                .html(cardChargingResponse.getHtml()).build();
                    } catch (Exception e) {
                        throw new WynkRuntimeException(PaymentErrorType.PAY041, e);
                    }
                }
            }
        }
    }


    private class NetBankingCharging implements IMerchantPaymentChargingServiceV2<AbstractCoreChargingResponse, AbstractChargingRequestV2> {

        private final Map<String, IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2>> netBankingDelegate = new HashMap<>();

        public NetBankingCharging () {
            netBankingDelegate.put(NET_BANKING, new NetBanking());
        }

        @Override
        public AbstractCoreChargingResponse charge (AbstractChargingRequestV2 request) {
            return netBankingDelegate.get(NET_BANKING).charge(request);
        }

        private class NetBanking implements IMerchantPaymentChargingServiceV2<AbstractCoreNetBankingChargingResponse, AbstractChargingRequestV2> {

            @Override
            public AbstractCoreNetBankingChargingResponse charge (AbstractChargingRequestV2 request) {
                final Transaction transaction = TransactionContext.get();
                final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
                final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                final String redirectUrl = ((IChargingDetails) request).getCallbackDetails().getCallbackUrl();
                final NetBankingPaymentInfo netBankingInfo =
                        NetBankingPaymentInfo.builder().bankCode((String) method.getMeta().get(PaymentConstants.BANK_CODE)).paymentAmount(transaction.getAmount()).paymentMode(NET_BANKING).build();
                final ApsExternalChargingRequest<NetBankingPaymentInfo> payRequest =
                        ApsExternalChargingRequest.<NetBankingPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(netBankingInfo)
                                .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                ApsNetBankingChargingResponse apsNetBankingChargingResponse =
                        common.exchange(CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, ApsNetBankingChargingResponse.class);
                return NetBankingHtmlTypeResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).html(apsNetBankingChargingResponse.getHtml()).build();
            }
        }
    }
}
