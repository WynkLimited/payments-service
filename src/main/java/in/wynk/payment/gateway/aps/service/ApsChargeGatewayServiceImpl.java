package in.wynk.payment.gateway.aps.service;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.FlowType;
import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.*;
import in.wynk.payment.dto.aps.request.charge.ExternalChargingRequest;
import in.wynk.payment.dto.aps.response.charge.CardChargingResponse;
import in.wynk.payment.dto.aps.response.charge.NetBankingChargingResponse;
import in.wynk.payment.dto.aps.response.charge.UpiCollectChargingResponse;
import in.wynk.payment.dto.aps.response.charge.UpiIntentChargingChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractCoreCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractNonSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.AbstractSeamlessCardChargingResponse;
import in.wynk.payment.dto.gateway.card.CardHtmlTypeChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.AbstractCoreNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NetBankingHtmlTypeResponse;
import in.wynk.payment.dto.gateway.upi.*;
import in.wynk.payment.dto.payu.PayUConstants;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.common.FreshCardDetails;
import in.wynk.payment.dto.request.common.SavedCardDetails;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.payment.service.PaymentCachingService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.constant.CardConstants.FRESH_CARD_TYPE;
import static in.wynk.payment.constant.FlowType.COLLECT;
import static in.wynk.payment.constant.FlowType.INTENT;
import static in.wynk.payment.constant.FlowType.UPI;
import static in.wynk.payment.constant.FlowType.*;
import static in.wynk.payment.constant.UpiConstants.*;
import static in.wynk.payment.dto.aps.common.ApsConstant.CURRENCY_INR;
import static in.wynk.payment.dto.aps.common.ApsConstant.APS_LOB_AUTO_PAY_REGISTER_WYNK;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsChargeGatewayServiceImpl implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

    private final String CHARGING_ENDPOINT;
    private final String UPI_CHARGING_ENDPOINT;

    private final ApsCommonGatewayService common;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final Map<FlowType, IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>> chargingDelegate = new HashMap<>();

    public ApsChargeGatewayServiceImpl (String upiChargeEndpoint, String commonChargeEndpoint, PaymentMethodCachingService paymentMethodCachingService, ApsCommonGatewayService common) {
        this.common = common;
        this.UPI_CHARGING_ENDPOINT = upiChargeEndpoint;
        this.CHARGING_ENDPOINT = commonChargeEndpoint;
        this.chargingDelegate.put(CARD, new CardCharging());
        this.chargingDelegate.put(UPI, new UpiCharging());
        this.chargingDelegate.put(NET_BANKING, new NetBankingCharging());
        this.paymentMethodCachingService = paymentMethodCachingService;
    }

    @Override
    public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
        final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
        return chargingDelegate.get(FlowType.valueOf(method.getGroup().toUpperCase(Locale.ROOT))).charge(request);
    }

    private class UpiCharging implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {
        private final Map<FlowType, IPaymentCharging<AbstractCoreUpiChargingResponse, AbstractPaymentChargingRequest>> upiDelegate = new HashMap<>();

        public UpiCharging () {
            upiDelegate.put(SEAMLESS, new UpiSeamlessCharging());
            upiDelegate.put(NON_SEAMLESS, new UpiNonSeamlessCharging());
        }

        @Override
        public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
            String flowType = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getFlowType();
            if (Objects.isNull(flowType)) {
                throw new WynkRuntimeException("flowType in configuration should not be null");
            }
            return upiDelegate.get(FlowType.valueOf(flowType)).charge(request);
        }

        private class UpiNonSeamlessCharging implements IPaymentCharging<AbstractCoreUpiChargingResponse, AbstractPaymentChargingRequest> {
            private final Map<FlowType, IPaymentCharging<AbstractNonSeamlessUpiChargingResponse, AbstractPaymentChargingRequest>> upiDelegate = new HashMap<>();

            public UpiNonSeamlessCharging () {
                upiDelegate.put(COLLECT, new UpiCollectCharging());
            }

            @Override
            public AbstractCoreUpiChargingResponse charge (AbstractPaymentChargingRequest request) {
                return upiDelegate.get(COLLECT).charge(request);
            }

            /**
             * UPI Collect redirect flow
             * If autoRenew in request is true means it was verified by other API call
             */
            private class UpiCollectCharging implements IPaymentCharging<AbstractNonSeamlessUpiChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public in.wynk.payment.dto.gateway.upi.UpiCollectChargingResponse charge (AbstractPaymentChargingRequest request) {
                    throw new WynkRuntimeException("This flow is not supported in APS");
                }
            }
        }

        private class UpiSeamlessCharging implements IPaymentCharging<AbstractCoreUpiChargingResponse, AbstractPaymentChargingRequest> {
            private final Map<FlowType, IPaymentCharging<AbstractSeamlessUpiChargingResponse, AbstractPaymentChargingRequest>> upiDelegate = new HashMap<>();

            public UpiSeamlessCharging () {
                upiDelegate.put(INTENT, new UpiIntentCharging());
                upiDelegate.put(COLLECT_IN_APP, new UpiCollectInAppCharging());
            }

            @Override
            public AbstractSeamlessUpiChargingResponse charge (AbstractPaymentChargingRequest request) {
                UpiPaymentDetails upiPaymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                FlowType flow = INTENT;
                if (!upiPaymentDetails.getUpiDetails().isIntent()) {
                    flow = COLLECT_IN_APP;
                }
                return upiDelegate.get(flow).charge(request);
            }

            private class UpiCollectInAppCharging implements IPaymentCharging<AbstractSeamlessUpiChargingResponse, AbstractPaymentChargingRequest> {
                @Override
                public UpiCollectInAppChargingResponse charge (AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    final String redirectUrl = request.getCallbackDetails().getCallbackUrl();
                    final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                    ExternalChargingRequest.ExternalChargingRequestBuilder<CollectUpiPaymentInfo> apsChargingRequestBuilder =
                            ExternalChargingRequest.<CollectUpiPaymentInfo>builder().orderId(transaction.getIdStr()).userInfo(userInfo)
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
                        paymentInfoBuilder.lob(APS_LOB_AUTO_PAY_REGISTER_WYNK).mandateAmount(transaction.getAmount()).paymentStartDate(today.toInstant().toEpochMilli())
                                .paymentEndDate(next10Year.toInstant().toEpochMilli());
                        apsChargingRequestBuilder.billPayment(false);
                    }
                    final ExternalChargingRequest<CollectUpiPaymentInfo> payRequest = apsChargingRequestBuilder.paymentInfo(paymentInfoBuilder.build()).build();
                    UpiCollectChargingResponse response =
                            common.exchange(UPI_CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, UpiCollectChargingResponse.class);
                    return UpiCollectInAppChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(updateTransactionType(response)).build();
                }
            }

            private class UpiIntentCharging implements IPaymentCharging<AbstractSeamlessUpiChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                @SneakyThrows
                public UpiIntentChargingResponse charge (AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    final String redirectUrl = request.getCallbackDetails().getCallbackUrl();
                    final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
                    final String payAppName = (String) method.getMeta().get(PaymentConstants.APP_NAME);
                    final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final IntentUpiPaymentInfo upiIntentDetails = IntentUpiPaymentInfo.builder().upiApp(payAppName).paymentAmount(transaction.getAmount()).build();
                    //TODO: Update request for UPI Intent once done from APS and call mandate creation
                    final ExternalChargingRequest<IntentUpiPaymentInfo> payRequest =
                            ExternalChargingRequest.<IntentUpiPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiIntentDetails)
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                    UpiIntentChargingChargingResponse apsUpiIntentChargingChargingResponse =
                            common.exchange(UPI_CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, UpiIntentChargingChargingResponse.class);
                    Map<String, String> map =
                            Arrays.stream(apsUpiIntentChargingChargingResponse.getUpiLink().split("\\?")[1].split("&")).map(s -> s.split("=", 2)).filter(p -> StringUtils.isNotBlank(p[1]))
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
            UpiCollectChargingResponse response) {
        if (Objects.isNull(response.getPgSystemId())) {
            return PaymentEvent.PURCHASE.getValue();
        }
        return PaymentEvent.SUBSCRIBE.getValue();
    }

    private class CardCharging implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

        private final Map<FlowType, IPaymentCharging<AbstractCoreCardChargingResponse, AbstractPaymentChargingRequest>> cardDelegate = new HashMap<>();

        public CardCharging () {
            cardDelegate.put(SEAMLESS, new CardSeamlessCharging());
            cardDelegate.put(NON_SEAMLESS, new CardNonSeamlessCharging());
        }

        @Override
        public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
            String flowType = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId()).getFlowType();
            if (Objects.isNull(flowType)) {
                throw new WynkRuntimeException("flowType in configuration should not be null");
            }
            return cardDelegate.get(FlowType.valueOf(flowType)).charge(request);
        }

        private class CardSeamlessCharging implements IPaymentCharging<AbstractCoreCardChargingResponse, AbstractPaymentChargingRequest> {
            private final Map<FlowType, IPaymentCharging<AbstractSeamlessCardChargingResponse, AbstractPaymentChargingRequest>> cardSeamlessDelegate = new HashMap<>();

            public CardSeamlessCharging () {
                cardSeamlessDelegate.put(INTENT, new CardOtpLessCharging());
                cardSeamlessDelegate.put(COLLECT_IN_APP, new CardInAppCharging());
            }

            @Override
            public AbstractSeamlessCardChargingResponse charge (AbstractPaymentChargingRequest request) {
                return null;
            }

            private class CardOtpLessCharging implements IPaymentCharging<AbstractSeamlessCardChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public AbstractSeamlessCardChargingResponse charge (AbstractPaymentChargingRequest request) {
                    return null;
                }
            }

            private class CardInAppCharging implements IPaymentCharging<AbstractSeamlessCardChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public AbstractSeamlessCardChargingResponse charge (AbstractPaymentChargingRequest request) {
                    return null;
                }
            }
        }

        private class CardNonSeamlessCharging implements IPaymentCharging<AbstractCoreCardChargingResponse, AbstractPaymentChargingRequest> {
            private final Map<FlowType, IPaymentCharging<AbstractNonSeamlessCardChargingResponse, AbstractPaymentChargingRequest>> cardNonSeamlessDelegate = new HashMap<>();

            public CardNonSeamlessCharging () {
                cardNonSeamlessDelegate.put(HTML, new CardHtmlTypeCharging());//APS supports html type
            }

            @Override
            public AbstractNonSeamlessCardChargingResponse charge (AbstractPaymentChargingRequest request) {
                return cardNonSeamlessDelegate.get(HTML).charge(request);
            }

            private class CardHtmlTypeCharging implements IPaymentCharging<AbstractNonSeamlessCardChargingResponse, AbstractPaymentChargingRequest> {

                @Override
                public AbstractNonSeamlessCardChargingResponse charge (AbstractPaymentChargingRequest request) {
                    final Transaction transaction = TransactionContext.get();
                    final CardPaymentDetails paymentDetails = (CardPaymentDetails) request.getPaymentDetails();
                    String paymentMode = "DEBIT_CARD";
                    if (Objects.nonNull(paymentDetails.getCardDetails().getCardInfo().getCategory()) &&
                            (paymentDetails.getCardDetails().getCardInfo().getCategory().equals("CREDIT") || paymentDetails.getCardDetails().getCardInfo().getCategory().equals("creditcard"))) {
                        paymentMode = "CREDIT_CARD";
                    }
                    AbstractCardPaymentInfo.AbstractCardPaymentInfoBuilder<?, ?> abstractCardPaymentInfoBuilder = null;
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
                        //auto-renew is supported only in case of Fresh card as all card details required for mandate creation
                        if (paymentDetails.isAutoRenew()) {
                            Calendar cal = Calendar.getInstance();
                            cal.add(Calendar.HOUR, 24);
                            Date today = cal.getTime();
                            cal.add(Calendar.YEAR, 10);
                            Date next10Year = cal.getTime();
                            abstractCardPaymentInfoBuilder.lob(APS_LOB_AUTO_PAY_REGISTER_WYNK)
                                    .productCategory(BaseConstants.WYNK)
                                    .mandateAmount(transaction.getAmount())
                                    .paymentStartDate(today.toInstant().toEpochMilli())
                                    .paymentEndDate(next10Year.toInstant().toEpochMilli());
                        }

                    } else {
                        final SavedCardDetails cardDetails = (SavedCardDetails) paymentDetails.getCardDetails();
                        final CardDetails credentials = CardDetails.builder().cvv(cardDetails.getCardInfo().getCvv()).cardRefNumber(cardDetails.getCardToken()).build();
                        final String encCardInfo = common.encryptCardData(credentials);
                        abstractCardPaymentInfoBuilder =
                                SavedCardPaymentInfo.builder().savedCardDetails(encCardInfo).paymentAmount(transaction.getAmount()).paymentMode(paymentMode);
                    }

                    final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final String redirectUrl = request.getCallbackDetails().getCallbackUrl();
                    ExternalChargingRequest<?> payRequest =
                            ExternalChargingRequest.builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(abstractCardPaymentInfoBuilder.build())
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                    CardChargingResponse cardChargingResponse =
                            common.exchange(CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, CardChargingResponse.class);
                    return CardHtmlTypeChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType("SUBSCRIBE")
                            .html(cardChargingResponse.getHtml()).build();
                }
            }
        }
    }

    private class NetBankingCharging implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

        private final Map<FlowType, IPaymentCharging<AbstractCoreNetBankingChargingResponse, AbstractPaymentChargingRequest>> netBankingDelegate = new HashMap<>();

        public NetBankingCharging () {
            netBankingDelegate.put(NET_BANKING, new NetBanking());
        }

        @Override
        public AbstractPaymentChargingResponse charge (AbstractPaymentChargingRequest request) {
            return netBankingDelegate.get(NET_BANKING).charge(request);
        }

        private class NetBanking implements IPaymentCharging<AbstractCoreNetBankingChargingResponse, AbstractPaymentChargingRequest> {

            @Override
            public AbstractCoreNetBankingChargingResponse charge (AbstractPaymentChargingRequest request) {
                final Transaction transaction = TransactionContext.get();
                final PaymentMethod method = paymentMethodCachingService.get(request.getPaymentDetails().getPaymentId());
                final UserInfo userInfo = UserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                final String redirectUrl = request.getCallbackDetails().getCallbackUrl();
                final NetBankingPaymentInfo netBankingInfo =
                        NetBankingPaymentInfo.builder().bankCode((String) method.getMeta().get(PaymentConstants.BANK_CODE)).paymentAmount(transaction.getAmount())
                                .paymentMode(NetBankingConstants.NET_BANKING).build();
                final ExternalChargingRequest<NetBankingPaymentInfo> payRequest =
                        ExternalChargingRequest.<NetBankingPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(netBankingInfo)
                                .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                NetBankingChargingResponse apsNetBankingChargingResponse =
                        common.exchange(CHARGING_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getUserDetails().getMsisdn()), payRequest, NetBankingChargingResponse.class);
                return NetBankingHtmlTypeResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).html(apsNetBankingChargingResponse.getHtml()).build();
            }
        }
    }
}
