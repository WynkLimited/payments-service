package in.wynk.payment.presentation;

import com.google.gson.Gson;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentChargingAction;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentMethodCachingService;
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
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.payment.constant.FlowType.NON_SEAMLESS;
import static in.wynk.payment.constant.FlowType.SEAMLESS;
import static in.wynk.payment.core.constant.CardConstants.CARD;
import static in.wynk.payment.core.constant.NetBankingConstants.NET_BANKING;
import static in.wynk.payment.core.constant.PaymentConstants.APP_PACKAGE;
import static in.wynk.payment.core.constant.UpiConstants.UPI;
import static in.wynk.payment.core.constant.UpiConstants.UPI_PREFIX;
import static in.wynk.payment.core.constant.WalletConstants.WALLET;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentChargingPresentationV2 implements IPaymentPresentationV2<PaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

    @Value("${payment.encKey}")
    private String encryptionKey;

    private final Gson gson;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final Map<String, IPaymentPresentationV2<? extends PaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> delegate = new HashMap<>();

    @PostConstruct
    public void init () {
        delegate.put(UPI, new UpiChargingPresentation());
        delegate.put(CARD, new CardChargingPresentation());
        delegate.put(NET_BANKING, new NetBankingChargingPresentation());
        delegate.put(WALLET, new WalletChargingPresentation());
    }

    @Override
    public PaymentChargingResponse transform(Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
        final PaymentMethod method = paymentMethodCachingService.get(payload.getFirst().getPaymentDetails().getPaymentId());
        return delegate.get(method.getGroup().toUpperCase()).transform(payload);
    }

    @SneakyThrows
    private String handleFormSpec (Map<String, String> form) {
        return EncryptionUtils.encrypt(gson.toJson(form), encryptionKey);
    }

    private class UpiChargingPresentation implements IPaymentPresentationV2<UpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

        private final Map<String, IPaymentPresentationV2<? extends UpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> upiDelegate = new HashMap<>();

        public UpiChargingPresentation () {
            upiDelegate.put(SEAMLESS.getValue(), new UpiSeamless());
            upiDelegate.put(NON_SEAMLESS.getValue(), new UpiNonSeamless());
        }

        @Override
        public UpiPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            String flowType = paymentMethodCachingService.get(payload.getFirst().getPaymentDetails().getPaymentId()).getFlowType();
            return upiDelegate.get(flowType).transform(payload);
        }

        public class UpiSeamless implements IPaymentPresentationV2<SeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends SeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> upiSeamlessDelegate = new HashMap<>();

            public UpiSeamless () {
                upiSeamlessDelegate.put("intent", new UpiSeamlessIntent());
                upiSeamlessDelegate.put("inApp", new UpiSeamlessCollectInApp());
            }

            @Override
            public SeamlessUpiPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return upiSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class UpiSeamlessIntent implements IPaymentPresentationV2<IntentSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @SneakyThrows
                @Override
                public IntentSeamlessUpiPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    UpiIntentChargingResponse response = (UpiIntentChargingResponse) payload.getSecond();
                    final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
                    final String prefix = (String) method.getMeta().getOrDefault(UPI_PREFIX, "upi");
                    final String stringBuilder = prefix +
                            ":" +
                            "//" +
                            "pay" +
                            "?pa=" + response.getPa() +
                            "&pn=" + response.getPn() +
                            "&tr=" + response.getTr() +
                            "&am=" + response.getAm() +
                            "&cu=" + response.getCu() +
                            "&tn=" + response.getTn() +
                            "&mc=" + response.getMc() +
                            "&tid=" + response.getTid();
                    final String form = EncryptionUtils.encrypt(stringBuilder, encryptionKey);
                    return IntentSeamlessUpiPaymentChargingResponse.builder()
                            .deepLink(form)
                            .appPackage((String) method.getMeta().get(APP_PACKAGE))
                            .action(PaymentChargingAction.INTENT.getAction())
                            .build();
                }
            }

            public class UpiSeamlessCollectInApp implements IPaymentPresentationV2<CollectInAppSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public CollectInAppSeamlessUpiPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY888);
                }
            }
        }

        public class UpiNonSeamless implements IPaymentPresentationV2<NonSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends NonSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> upiNonSeamlessDelegate = new HashMap<>();

            public UpiNonSeamless () {
                upiNonSeamlessDelegate.put("collect", new UpiNonSeamlessCollect());
            }

            @Override
            public NonSeamlessUpiPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return upiNonSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class UpiNonSeamlessCollect implements IPaymentPresentationV2<CollectNonSeamlessUpiPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @SneakyThrows
                @Override
                public CollectNonSeamlessUpiPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    final UpiCollectChargingResponse response = (UpiCollectChargingResponse) payload.getSecond();
                    final String url = EncryptionUtils.encrypt(response.getUrl(), encryptionKey);
                    return CollectNonSeamlessUpiPaymentChargingResponse.builder()
                            .url(url).action(PaymentChargingAction.REDIRECT.getAction()).build();
                }
            }
        }
    }

    private class CardChargingPresentation implements IPaymentPresentationV2<CardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

        private final Map<String, IPaymentPresentationV2<? extends CardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> cardDelegate = new HashMap<>();

        public CardChargingPresentation () {
            cardDelegate.put(SEAMLESS.getValue(), new CardSeamless());
            cardDelegate.put(NON_SEAMLESS.getValue(), new CardNonSeamless());
        }

        @Override
        public CardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            final PaymentMethod method = paymentMethodCachingService.get(payload.getFirst().getPaymentDetails().getPaymentId());
            final CardPaymentDetails paymentDetails = (CardPaymentDetails) payload.getFirst().getPaymentDetails();
            boolean inAppOtpSupport = (Objects.isNull(paymentDetails.getCardDetails().getInAppOtpSupport())) ? method.isInAppOtpSupport() : paymentDetails.getCardDetails().getInAppOtpSupport();
            boolean isOtpLessSupport = (Objects.isNull(paymentDetails.getCardDetails().getOtpLessSupport())) ? method.isOtpLessSupport() : paymentDetails.getCardDetails().getOtpLessSupport();
            String flowType = (inAppOtpSupport || isOtpLessSupport) ? SEAMLESS.getValue() : NON_SEAMLESS.getValue();
            return cardDelegate.get(flowType).transform(payload);
        }

        public class CardSeamless implements IPaymentPresentationV2<SeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends SeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> cardSeamlessDelegate = new HashMap<>();

            public CardSeamless () {
                cardSeamlessDelegate.put("otpLess", new CardSeamlessOtpLess());
                cardSeamlessDelegate.put("inApp", new CardSeamlessInApp());
            }

            @Override
            public SeamlessCardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return cardSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class CardSeamlessOtpLess implements IPaymentPresentationV2<OtpLessSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public OtpLessSeamlessCardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY888);
                }
            }

            public class CardSeamlessInApp implements IPaymentPresentationV2<InAppSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public InAppSeamlessCardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY888);
                }
            }
        }

        public class CardNonSeamless implements IPaymentPresentationV2<NonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {

            private final Map<String, IPaymentPresentationV2<? extends NonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>>> cardNonSeamlessDelegate = new HashMap<>();

            public CardNonSeamless () {
                cardNonSeamlessDelegate.put("keyValue", new CardNonSeamlessKeyValueType());
                cardNonSeamlessDelegate.put("html", new CardNonSeamlessHtmlType());
            }

            @Override
            public NonSeamlessCardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return cardNonSeamlessDelegate.get(payment.mode()).transform(payload);
            }

            public class CardNonSeamlessKeyValueType implements IPaymentPresentationV2<KeyValueTypeNonSeamlessCardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
                @Override
                public KeyValueTypeNonSeamlessCardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
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

        public NetBankingChargingPresentation () {
            nbDelegate.put(SEAMLESS.getValue(), new NetBankingSeamless());
            nbDelegate.put(NON_SEAMLESS.getValue(), new NetBankingNonSeamless());
        }
        @Override
        public NetBankingPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
            return nbDelegate.get(method.getFlowType()).transform(payload);
        }

        public class NetBankingSeamless implements IPaymentPresentationV2<SeamlessNetBankingPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
            @Override
            public SeamlessNetBankingPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                throw new WynkRuntimeException(PaymentErrorType.PAY888);
            }
        }
        public class NetBankingNonSeamless implements IPaymentPresentationV2<NonSeamlessNetBankingPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
            @Override
            public NonSeamlessNetBankingPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
                final NonSeamlessNetBankingChargingResponse response = (NonSeamlessNetBankingChargingResponse) payload.getSecond();
                final String encForm = PaymentChargingPresentationV2.this.handleFormSpec(response.getForm());
                return NonSeamlessNetBankingPaymentChargingResponse.builder()
                        .form(encForm).action(PaymentChargingAction.KEY_VALUE.getAction()).build();
            }
        }
    }

    private class WalletChargingPresentation implements IPaymentPresentationV2<CardPaymentChargingResponse, Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse>> {
        @Override
        public CardPaymentChargingResponse transform (Pair<AbstractChargingRequestV2, AbstractCoreChargingResponse> payload) {
            return null;
        }
    }
}