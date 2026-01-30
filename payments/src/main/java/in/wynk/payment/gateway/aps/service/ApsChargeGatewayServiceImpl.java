package in.wynk.payment.gateway.aps.service;

import com.github.annotation.analytic.core.service.AnalyticService;
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
import in.wynk.payment.dto.aps.request.charge.ChannelInfo;
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
import in.wynk.payment.event.PaymentChargingKafkaMessage;
import in.wynk.payment.gateway.IPaymentCharging;
import in.wynk.stream.service.IDataPlatformKafkaService;
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
import static in.wynk.payment.core.constant.BeanConstant.AIRTEL_PAY_STACK_V2;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsChargeGatewayServiceImpl implements IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest> {

    private final String CARD_NETBANKING_CHARGING_ENDPOINT;
    private final String UPI_CHARGING_ENDPOINT;
    private final String UPI_PAYDIGI_CHARGING_ENDPOINT;
    private final String CARD_NETBANKING_PAYDIGI_CHARGING_ENDPOINT;

    private final ApsCommonGatewayService common;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IDataPlatformKafkaService dataPlatformKafkaService;
    private final Map<FlowType, IPaymentCharging<AbstractPaymentChargingResponse, AbstractPaymentChargingRequest>> chargingDelegate = new HashMap<>();

    public ApsChargeGatewayServiceImpl(String upiChargeEndpoint, String chargeEndpoint, String upiPayDigiChargeEndpoint, String payDigiChargeEndpoint, PaymentMethodCachingService paymentMethodCachingService, ApsCommonGatewayService common, IDataPlatformKafkaService dataPlatformKafkaService) {
        this.common = common;
        this.UPI_CHARGING_ENDPOINT = upiChargeEndpoint;
        this.CARD_NETBANKING_CHARGING_ENDPOINT = chargeEndpoint;
        this.UPI_PAYDIGI_CHARGING_ENDPOINT = upiPayDigiChargeEndpoint;
        this.CARD_NETBANKING_PAYDIGI_CHARGING_ENDPOINT = payDigiChargeEndpoint;
        this.chargingDelegate.put(CARD, new CardCharging());
        this.chargingDelegate.put(UPI, new UpiCharging());
        this.chargingDelegate.put(NET_BANKING, new NetBankingCharging());
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.dataPlatformKafkaService = dataPlatformKafkaService;
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
                    boolean isRecharge = transaction.getPaymentChannel().getCode().equals(AIRTEL_PAY_STACK_V2);
                    final AbstractUserInfo userInfo = isRecharge ? OrderUserInfo.builder().serviceInstance(common.getLoginId(request.getUserDetails().getMsisdn())).build() :
                            ChargeUserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                    ExternalChargingRequest.ExternalChargingRequestBuilder<CollectUpiPaymentInfo> apsChargingRequestBuilder =
                            ExternalChargingRequest.<CollectUpiPaymentInfo>builder().orderId(isRecharge ? request.getOrderId() : transaction.getIdStr()).userInfo(userInfo)
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build());
                    boolean isMandateFlow = paymentDetails.isMandate() || request.getPaymentDetails().isTrialOpted();
                    CollectUpiPaymentInfo.CollectUpiPaymentInfoBuilder<?, ?> paymentInfoBuilder =
                            CollectUpiPaymentInfo.builder().lob(isRecharge ? LOB.PREPAID.toString() : LOB.WYNK.toString()).vpa(paymentDetails.getUpiDetails().getVpa()).paymentAmount(isMandateFlow ? PaymentConstants.MANDATE_FLOW_AMOUNT : transaction.getAmount()).paymentMode(request.getPaymentDetails().getPaymentMode());
                    //if auto-renew true means user's mandate should be registered. Update fields in request for autoRenew
                    if (paymentDetails.isAutoRenew() || isMandateFlow ) {
                        Calendar cal = Calendar.getInstance();
                        Date today = cal.getTime();
                        cal.add(Calendar.YEAR, 10); // 10 yrs from now
                        Date next10Year = cal.getTime();
                        paymentInfoBuilder.lob(LOB.AUTO_PAY_REGISTER_WYNK.toString()).productCategory(BaseConstants.WYNK).mandateAmount(transaction.getMandateAmount())
                                .paymentStartDate(today.toInstant().toEpochMilli())
                                .paymentEndDate(next10Year.toInstant().toEpochMilli()).billPayment(!isMandateFlow);
                    }
                    final ExternalChargingRequest<CollectUpiPaymentInfo> payRequest = apsChargingRequestBuilder.paymentInfo(paymentInfoBuilder.build()).build();
                    AnalyticService.update("paymentModeOfPayRequest", payRequest.getPaymentInfo().getPaymentMode());
                    common.exchange(transaction.getClientAlias(), isRecharge ? UPI_PAYDIGI_CHARGING_ENDPOINT : UPI_CHARGING_ENDPOINT, HttpMethod.POST,
                            isRecharge ? null : request.getUserDetails().getMsisdn(), payRequest, UpiCollectChargingResponse.class);
                    updateToKafka(request, transaction);
                    return UpiCollectInAppChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue()).build();
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
                    boolean isRecharge = transaction.getPaymentChannel().getCode().equals(AIRTEL_PAY_STACK_V2);
                    final AbstractUserInfo userInfo = isRecharge ? OrderUserInfo.builder().serviceInstance(common.getLoginId(request.getUserDetails().getMsisdn())).build() :
                            ChargeUserInfo.builder().loginId(common.getLoginId(request.getUserDetails().getMsisdn())).build();
                    final UpiPaymentDetails paymentDetails = (UpiPaymentDetails) request.getPaymentDetails();
                    ExternalChargingRequest.ExternalChargingRequestBuilder<IntentUpiPaymentInfo> apsChargingRequestBuilder =
                            ExternalChargingRequest.<IntentUpiPaymentInfo>builder().userInfo(userInfo).orderId(isRecharge ? request.getOrderId() : transaction.getIdStr())
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build());
                    boolean isMandateFlow = paymentDetails.isMandate() || request.getPaymentDetails().isTrialOpted();
                    IntentUpiPaymentInfo.IntentUpiPaymentInfoBuilder<?, ?> upiPaymentInfoBuilder =
                            IntentUpiPaymentInfo.builder().lob(isRecharge ? LOB.PREPAID.toString() : LOB.WYNK.toString()).upiApp(payAppName)
                                    .paymentAmount(isMandateFlow ? PaymentConstants.MANDATE_FLOW_AMOUNT : transaction.getAmount()).paymentMode(request.getPaymentDetails().getPaymentMode());
                    //if auto-renew true means user's mandate should be registered. Update fields in request for autoRenew
                    if (paymentDetails.isAutoRenew() || isMandateFlow) {
                        Calendar cal = Calendar.getInstance();
                        Date today = cal.getTime();
                        cal.add(Calendar.YEAR, 10); // 10 yrs from now
                        Date next10Year = cal.getTime();
                        upiPaymentInfoBuilder.lob(LOB.AUTO_PAY_REGISTER_WYNK.toString())
                                .productCategory(BaseConstants.WYNK)
                                .mandateAmount(transaction.getMandateAmount())
                                .paymentStartDate(today.toInstant().toEpochMilli())
                                .paymentEndDate(next10Year.toInstant().toEpochMilli()).billPayment(!isMandateFlow);
                    }

                    ExternalChargingRequest<IntentUpiPaymentInfo> payRequest = apsChargingRequestBuilder.paymentInfo(upiPaymentInfoBuilder.build()).build();
                    AnalyticService.update("paymentModeOfPayRequest", payRequest.getPaymentInfo().getPaymentMode());
                    UpiIntentChargingChargingResponse apsUpiIntentChargingChargingResponse =
                            common.exchange(transaction.getClientAlias(), isRecharge ? UPI_PAYDIGI_CHARGING_ENDPOINT : UPI_CHARGING_ENDPOINT, HttpMethod.POST,
                                    isRecharge ? null : request.getUserDetails().getMsisdn(),
                                    payRequest, UpiIntentChargingChargingResponse.class);
                    Map<String, String> map =
                            Arrays.stream(apsUpiIntentChargingChargingResponse.getUpiLink().split("\\?")[1].split("&")).map(s -> s.split("=", 2)).filter(p -> StringUtils.isNotBlank(p[1]))
                                    .collect(Collectors.toMap(x -> x[0], x -> x[1]));
                    PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
                    String offerTitle;
                    if (transaction.getType() == PaymentEvent.POINT_PURCHASE) {
                        offerTitle = transaction.getItemId();
                    } else {
                        offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();
                    }
                    updateToKafka(request, transaction);
                    return UpiIntentChargingResponse.builder()
                            .mn(map.get(MN))
                            .rev(map.get(REV))
                            .mode(map.get(MODE))
                            .recur(map.get(RECUR))
                            .block(map.get(BLOCK))
                            .orgId(map.get(ORG_ID))
                            .amRule(map.get(AM_RULE))
                            .purpose(map.get(PURPOSE))
                            .txnType(map.get(TXN_TYPE))
                            .pa(map.get(PA))
                            .tid(transaction.getIdStr())
                            .recurType(map.get(RECUR_TYPE))
                            .pn(PaymentConstants.DEFAULT_PN)
                            .recurValue(map.get(RECUR_VALUE))
                            .validityEnd(map.get(VALIDITY_END))
                            .validityStart(map.get(VALIDITY_START))
                            .cu(map.getOrDefault(CU, PaymentConstants.CURRENCY_INR))
                            .transactionStatus(transaction.getStatus())
                            .tr(map.get(TR))
                            .am(map.get(AM))
                            .fam(map.get(FAM))
                            .transactionType(transaction.getType().getValue())
                            .tn(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get(TN))
                            .mc(PayUConstants.PAYU_MERCHANT_CODE)
                            .build();
                }
            }
        }
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
                    if ("CC".equals(paymentDetails.getCardDetails().getCardInfo().getCategory())) {
                        paymentMode = "CREDIT_CARD";
                    }
                    boolean isRecharge = transaction.getPaymentChannel().getCode().equals(AIRTEL_PAY_STACK_V2);
                    final AbstractUserInfo userInfo = isRecharge ? OrderUserInfo.builder().serviceInstance(common.getLoginId(request.getUserDetails().getMsisdn())).build() :
                            ChargeUserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                    AbstractCardPaymentInfo.AbstractCardPaymentInfoBuilder<?, ?> abstractCardPaymentInfoBuilder = null;
                    String lob = isRecharge ? LOB.PREPAID.toString() : LOB.WYNK.toString();
                    if (FRESH_CARD_TYPE.equals(paymentDetails.getCardDetails().getType())) {
                        final FreshCardDetails cardDetails = (FreshCardDetails) paymentDetails.getCardDetails();
                        final CardDetails credentials =
                                CardDetails.builder().nameOnCard(((FreshCardDetails) paymentDetails.getCardDetails()).getCardHolderName()).cardNumber(cardDetails.getCardNumber())
                                        .expiryMonth(cardDetails.getExpiryInfo().getMonth()).expiryYear(cardDetails.getExpiryInfo().getYear()).cvv(cardDetails.getCardInfo().getCvv()).build();
                        final String encCardInfo = common.encryptCardData(credentials);
                        boolean isMandateFlow = paymentDetails.isMandate() || request.getPaymentDetails().isTrialOpted();
                        abstractCardPaymentInfoBuilder =
                                FreshCardPaymentInfo.builder().lob(lob).cardDetails(encCardInfo).saveCard(cardDetails.isSaveCard())
                                        .favouriteCard(cardDetails.isSaveCard()).tokenizeConsent(cardDetails.isSaveCard())
                                        .paymentMode(paymentMode).paymentAmount(isMandateFlow ? PaymentConstants.MANDATE_FLOW_AMOUNT : transaction.getAmount());
                        //auto-renew is supported only in case of Fresh card as all card details required for mandate creation
                        if (paymentDetails.isAutoRenew() || isMandateFlow) {
                            Calendar cal = Calendar.getInstance();
                            Date today = cal.getTime();
                            cal.add(Calendar.YEAR, 10);
                            Date next10Year = cal.getTime();
                            abstractCardPaymentInfoBuilder.lob(LOB.AUTO_PAY_REGISTER_WYNK.toString())
                                    .productCategory(BaseConstants.WYNK)
                                    .mandateAmount(transaction.getMandateAmount())
                                    .paymentStartDate(today.toInstant().toEpochMilli())
                                    .paymentEndDate(next10Year.toInstant().toEpochMilli()).billPayment(!isMandateFlow);

                        }
                    } else {
                        final SavedCardDetails cardDetails = (SavedCardDetails) paymentDetails.getCardDetails();
                        final CardDetails credentials = CardDetails.builder().cvv(cardDetails.getCardInfo().getCvv()).cardRefNumber(cardDetails.getCardToken()).build();
                        final String encCardInfo = common.encryptCardData(credentials);
                        abstractCardPaymentInfoBuilder =
                                SavedCardPaymentInfo.builder().lob(lob).savedCardDetails(encCardInfo).paymentAmount(transaction.getAmount()).paymentMode(paymentMode);
                    }
                    final String redirectUrl = request.getCallbackDetails().getCallbackUrl();
                    ExternalChargingRequest<?> payRequest =
                            ExternalChargingRequest.builder().userInfo(userInfo).orderId(isRecharge ? request.getOrderId() : transaction.getIdStr()).paymentInfo(abstractCardPaymentInfoBuilder.build())
                                    .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                    CardChargingResponse cardChargingResponse =
                            common.exchange(transaction.getClientAlias(), isRecharge ? CARD_NETBANKING_PAYDIGI_CHARGING_ENDPOINT : CARD_NETBANKING_CHARGING_ENDPOINT, HttpMethod.POST,
                                    isRecharge ? null : request.getUserDetails().getMsisdn(), payRequest, CardChargingResponse.class);
                    updateToKafka(request, transaction);
                    return CardHtmlTypeChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType().getValue())
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
                boolean isRecharge = transaction.getPaymentChannel().getCode().equals(AIRTEL_PAY_STACK_V2);
                final AbstractUserInfo userInfo = isRecharge ? OrderUserInfo.builder().serviceInstance(common.getLoginId(request.getUserDetails().getMsisdn())).build() :
                        ChargeUserInfo.builder().loginId(request.getUserDetails().getMsisdn()).build();
                final String redirectUrl = request.getCallbackDetails().getCallbackUrl();
                final NetBankingPaymentInfo netBankingInfo =
                        NetBankingPaymentInfo.builder().lob(isRecharge ? LOB.PREPAID.toString() : LOB.WYNK.toString()).bankCode((String) method.getMeta().get(PaymentConstants.BANK_CODE)).paymentAmount(transaction.getAmount())
                                .paymentMode(NetBankingConstants.NET_BANKING).build();
                final ExternalChargingRequest<NetBankingPaymentInfo> payRequest =
                        ExternalChargingRequest.<NetBankingPaymentInfo>builder().userInfo(userInfo).orderId(isRecharge ? request.getOrderId() : transaction.getIdStr()).paymentInfo(netBankingInfo)
                                .channelInfo(ChannelInfo.builder().redirectionUrl(redirectUrl).build()).build();
                NetBankingChargingResponse apsNetBankingChargingResponse =
                        common.exchange(transaction.getClientAlias(), isRecharge ? CARD_NETBANKING_PAYDIGI_CHARGING_ENDPOINT : CARD_NETBANKING_CHARGING_ENDPOINT, HttpMethod.POST,
                                isRecharge ? null : request.getUserDetails().getMsisdn(), payRequest, NetBankingChargingResponse.class);
                updateToKafka(request, transaction);
                return NetBankingHtmlTypeResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).html(apsNetBankingChargingResponse.getHtml()).build();
            }
        }
    }

    public void updateToKafka(AbstractPaymentChargingRequest request, Transaction transaction) {
        dataPlatformKafkaService.publish(PaymentChargingKafkaMessage.from(request, transaction));
    }
}
