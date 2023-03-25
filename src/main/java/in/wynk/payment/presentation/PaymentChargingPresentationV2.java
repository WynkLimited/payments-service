package in.wynk.payment.presentation;

import com.google.gson.Gson;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentChargingAction;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.PollingConfig;
import in.wynk.payment.dto.gateway.card.CardHtmlTypeChargingResponse;
import in.wynk.payment.dto.gateway.card.CardKeyValueTypeChargingResponse;
import in.wynk.payment.dto.gateway.netbanking.NonSeamlessNetBankingChargingResponse;
import in.wynk.payment.dto.gateway.upi.UpiCollectChargingResponse;
import in.wynk.payment.dto.gateway.upi.UpiIntentChargingResponse;
import in.wynk.payment.dto.request.AbstractChargingRequestV2;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import in.wynk.payment.presentation.dto.charge.PaymentChargingResponse;
import in.wynk.payment.presentation.dto.charge.card.*;
import in.wynk.payment.presentation.dto.charge.netbanking.NetBankingPaymentChargingResponse;
import in.wynk.payment.presentation.dto.charge.netbanking.NonSeamlessNetBankingPaymentChargingResponse;
import in.wynk.payment.presentation.dto.charge.netbanking.SeamlessNetBankingPaymentChargingResponse;
import in.wynk.payment.presentation.dto.charge.upi.*;
import in.wynk.queue.dto.Payment;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.payment.core.constant.CardConstants.CARD;
import static in.wynk.payment.core.constant.NetBankingConstants.NET_BANKING;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.UpiConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentChargingPresentationV2 implements IPaymentPresentationV2<PaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

    @Value("${payment.encKey}")
    private String ENC_KEY;
    @Value("${payment.polling.page}")
    private String CLIENT_POLLING_SCREEN_URL;

    private final Gson gson;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;
    private final Map<String, IPaymentPresentationV2<? extends PaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> delegate = new HashMap<>();

    @PostConstruct
    public void init() {
        delegate.put(UPI, new UpiChargingPresentation());
        delegate.put(CARD, new CardChargingPresentation());
        delegate.put(NET_BANKING, new NetBankingChargingPresentation());
        delegate.put(WALLET, new WalletChargingPresentation());
    }

    @Override
    public PaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
        final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
        return delegate.get(method.getGroup().toUpperCase()).transform(payload);
    }

    @SneakyThrows
    private String handleFormSpec(Map<String, String> form) {
        return EncryptionUtils.encrypt(gson.toJson(form), ENC_KEY);
    }

    private class UpiChargingPresentation implements IPaymentPresentationV2<UpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

        private final Map<String, IPaymentPresentationV2<? extends UpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> upiDelegate = new HashMap<>();

        public UpiChargingPresentation() {
            upiDelegate.put(SEAMLESS, new UpiSeamless());
            upiDelegate.put(NON_SEAMLESS, new UpiNonSeamless());
        }

        @Override
        public UpiPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            String flowType = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId()).getFlowType();
            return upiDelegate.get(flowType).transform(payload);
        }

        public class UpiSeamless implements IPaymentPresentationV2<SeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends SeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> upiSeamlessDelegate = new HashMap<>();

            public UpiSeamless() {
                upiSeamlessDelegate.put(INTENT, new UpiSeamlessIntent());
                upiSeamlessDelegate.put(INAPP, new UpiSeamlessCollectInApp());
            }

            @Override
            public SeamlessUpiPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return upiSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class UpiSeamlessIntent implements IPaymentPresentationV2<IntentSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @SneakyThrows
                @Override
                public IntentSeamlessUpiPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    AbstractChargingRequestV2 request = payload.getFirst();
                    UpiIntentChargingResponse response = (UpiIntentChargingResponse) payload.getSecond();
                    final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
                    final String prefix = (String) method.getMeta().getOrDefault(UPI_PREFIX, "upi");
                    StringBuilder stringBuilder = new StringBuilder(prefix);
                    if (!request.isAutoRenewOpted()) stringBuilder.append("://pay?");
                    else {
                        stringBuilder.append("://mandate?");
                        if (!StringUtils.isEmpty(response.getMn()))
                            stringBuilder.append("&mn=").append(response.getMn());
                        if (!StringUtils.isEmpty(response.getRev()))
                            stringBuilder.append("&rev=").append(response.getRev());
                        if (!StringUtils.isEmpty(response.getMode()))
                            stringBuilder.append("&mode=").append(response.getMode());
                        if (!StringUtils.isEmpty(response.getRecur()))
                            stringBuilder.append("&recur=").append(response.getRecur());
                        if (!StringUtils.isEmpty(response.getOrgId()))
                            stringBuilder.append("&orgid=").append(response.getOrgId());
                        if (!StringUtils.isEmpty(response.getBlock()))
                            stringBuilder.append("&block=").append(response.getBlock());
                        if (!StringUtils.isEmpty(response.getAmRule()))
                            stringBuilder.append("&amrule=").append(response.getAmRule());
                        if (!StringUtils.isEmpty(response.getPurpose()))
                            stringBuilder.append("&purpose=").append(response.getPurpose());
                        if (!StringUtils.isEmpty(response.getTxnType()))
                            stringBuilder.append("&txnType=").append(response.getTxnType());
                        if (!StringUtils.isEmpty(response.getRecurType()))
                            stringBuilder.append("&recurtype=").append(response.getRecurType());
                        if (!StringUtils.isEmpty(response.getRecurValue()))
                            stringBuilder.append("&recurvalue=").append(response.getRecurValue());
                        if (!StringUtils.isEmpty(response.getValidityEnd()))
                            stringBuilder.append("&validityend=").append(response.getValidityEnd());
                        if (!StringUtils.isEmpty(response.getValidityStart()))
                            stringBuilder.append("&validitystart=").append(response.getValidityStart());
                        stringBuilder.append("&");
                    }
                    stringBuilder.append("pa=").append(response.getPa());
                    stringBuilder.append("&pn=").append(response.getPn());
                    stringBuilder.append("&tr=").append(response.getTr());
                    stringBuilder.append("&am=").append(response.getAm());
                    stringBuilder.append("&cu=").append(response.getCu());
                    stringBuilder.append("&tn=").append(response.getTn());
                    stringBuilder.append("&mc=").append(response.getMc());
                    stringBuilder.append("&tid=").append(response.getTid().replaceAll("-", ""));
                    final String form = EncryptionUtils.encrypt(stringBuilder.toString(), ENC_KEY);
                    return IntentSeamlessUpiPaymentChargingResponse.builder()
                            .deepLink(form)
                            .action(PaymentChargingAction.INTENT.getAction())
                            .appPackage((String) method.getMeta().get(APP_PACKAGE))
                            .pollingConfig(buildPollingConfig(payload.getFirst().getPaymentId()))
                            .build();
                }
            }

            public class UpiSeamlessCollectInApp implements IPaymentPresentationV2<CollectInAppSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public CollectInAppSeamlessUpiPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    final SessionDTO sessionDTO = SessionContextHolder.getBody();
                    final String os = sessionDTO.get(OS);
                    final String sid = SessionContextHolder.getId();
                    final String url = CLIENT_POLLING_SCREEN_URL.concat(sid).concat(SLASH).concat(os);
                    return CollectInAppSeamlessUpiPaymentChargingResponse.builder().url(url).action(PaymentChargingAction.REDIRECT.getAction()).pollingConfig(buildPollingConfig(payload.getFirst().getPaymentId())).build();
                }
            }
        }

        public class UpiNonSeamless implements IPaymentPresentationV2<NonSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends NonSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> upiNonSeamlessDelegate = new HashMap<>();

            public UpiNonSeamless() {
                upiNonSeamlessDelegate.put("COLLECT", new UpiNonSeamlessCollect());
            }

            @Override
            public NonSeamlessUpiPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return upiNonSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class UpiNonSeamlessCollect implements IPaymentPresentationV2<CollectNonSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @SneakyThrows
                @Override
                public CollectNonSeamlessUpiPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    UpiCollectChargingResponse response = (UpiCollectChargingResponse) payload.getSecond();
                    final String encForm = PaymentChargingPresentationV2.this.handleFormSpec(response.getForm());
                    return CollectNonSeamlessUpiPaymentChargingResponse.builder()
                            .form(encForm).action(PaymentChargingAction.KEY_VALUE.getAction()).build();
                }
            }
        }
    }

    private class CardChargingPresentation implements IPaymentPresentationV2<CardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

        private final Map<String, IPaymentPresentationV2<? extends CardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> cardDelegate = new HashMap<>();

        public CardChargingPresentation() {
            cardDelegate.put(SEAMLESS, new CardSeamless());
            cardDelegate.put(NON_SEAMLESS, new CardNonSeamless());
        }

        @Override
        public CardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
            final CardPaymentDetails paymentDetails = (CardPaymentDetails) payload.getFirst().getPaymentDetails();
            boolean inAppOtpSupport = (Objects.isNull(paymentDetails.getCardDetails().getInAppOtpSupport())) ? method.isInAppOtpSupport() : paymentDetails.getCardDetails().getInAppOtpSupport();
            boolean isOtpLessSupport = (Objects.isNull(paymentDetails.getCardDetails().getOtpLessSupport())) ? method.isOtpLessSupport() : paymentDetails.getCardDetails().getOtpLessSupport();
            String flowType = (inAppOtpSupport || isOtpLessSupport) ? SEAMLESS : NON_SEAMLESS;
            return cardDelegate.get(flowType).transform(payload);
        }

        public class CardSeamless implements IPaymentPresentationV2<SeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends SeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> cardSeamlessDelegate = new HashMap<>();

            public CardSeamless() {
                cardSeamlessDelegate.put(OTP_LESS, new CardSeamlessOtpLess());
                cardSeamlessDelegate.put(INAPP, new CardSeamlessInApp());
            }

            @Override
            public SeamlessCardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return cardSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class CardSeamlessOtpLess implements IPaymentPresentationV2<OtpLessSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public OtpLessSeamlessCardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY888);
                }
            }

            public class CardSeamlessInApp implements IPaymentPresentationV2<InAppSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public InAppSeamlessCardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY888);
                }
            }
        }

        public class CardNonSeamless implements IPaymentPresentationV2<NonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends NonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> cardNonSeamlessDelegate = new HashMap<>();

            public CardNonSeamless() {
                cardNonSeamlessDelegate.put(KEY_VALUE, new CardNonSeamlessKeyValueType());
                cardNonSeamlessDelegate.put(HTML, new CardNonSeamlessHtmlType());
            }

            @Override
            public NonSeamlessCardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return cardNonSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class CardNonSeamlessKeyValueType implements IPaymentPresentationV2<KeyValueTypeNonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public KeyValueTypeNonSeamlessCardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    final CardKeyValueTypeChargingResponse response = (CardKeyValueTypeChargingResponse) payload.getSecond();
                    final String encForm = PaymentChargingPresentationV2.this.handleFormSpec(response.getForm());
                    return KeyValueTypeNonSeamlessCardPaymentChargingResponse.builder()
                            .form(encForm).action(PaymentChargingAction.KEY_VALUE.getAction()).build();
                }
            }

            public class CardNonSeamlessHtmlType implements IPaymentPresentationV2<HtmlTypeNonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public HtmlTypeNonSeamlessCardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    final CardHtmlTypeChargingResponse response = (CardHtmlTypeChargingResponse) payload.getSecond();
                    return HtmlTypeNonSeamlessCardPaymentChargingResponse.builder()
                            .html(response.getHtml()).action(PaymentChargingAction.HTML.getAction()).build();
                }
            }
        }
    }

    private class NetBankingChargingPresentation implements IPaymentPresentationV2<NetBankingPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

        private final Map<String, IPaymentPresentationV2<? extends NetBankingPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> nbDelegate = new HashMap<>();

        public NetBankingChargingPresentation() {
            nbDelegate.put(SEAMLESS, new NetBankingSeamless());
            nbDelegate.put(NON_SEAMLESS, new NetBankingNonSeamless());
        }

        @Override
        public NetBankingPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
            return nbDelegate.get(method.getFlowType()).transform(payload);
        }

        public class NetBankingSeamless implements IPaymentPresentationV2<SeamlessNetBankingPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
            @Override
            public SeamlessNetBankingPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                throw new WynkRuntimeException(PaymentErrorType.PAY888);
            }
        }

        public class NetBankingNonSeamless implements IPaymentPresentationV2<NonSeamlessNetBankingPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
            @Override
            public NonSeamlessNetBankingPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final NonSeamlessNetBankingChargingResponse response = (NonSeamlessNetBankingChargingResponse) payload.getSecond();
                final String encForm = PaymentChargingPresentationV2.this.handleFormSpec(response.getForm());
                return NonSeamlessNetBankingPaymentChargingResponse.builder()
                        .form(encForm).action(PaymentChargingAction.KEY_VALUE.getAction()).build();
            }
        }
    }

    private class WalletChargingPresentation implements IPaymentPresentationV2<CardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
        @Override
        public CardPaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            return null;
        }
    }

    private PollingConfig buildPollingConfig(String payId) {
        Map<String, Object> meta = paymentMethodCache.get(payId).getMeta();
        long timer = (long) meta.getOrDefault(PAYMENT_TIMER_KEY, 40);
        long interval = (long) meta.getOrDefault(PAYMENT_STATUS_POLL_KEY, 10);
        return PollingConfig.builder().interval(interval).frequency(timer / interval).timeout(timer).build();
    }
}