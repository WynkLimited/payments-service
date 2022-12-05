package in.wynk.payment.presentation;

import com.google.gson.Gson;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.common.IPostFormSpec;
import in.wynk.payment.dto.common.IUpiIntentSpec;
import in.wynk.payment.dto.common.response.ChargingResponseWrapper;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.presentation.card.AbstractCardChargingResponse;
import in.wynk.payment.dto.response.presentation.card.NonSeamlessCardChargingResponse;
import in.wynk.payment.dto.response.presentation.card.SeamlessCardChargingResponse;
import in.wynk.payment.dto.response.presentation.netbanking.AbstractNetBankingChargingResponse;
import in.wynk.payment.dto.response.presentation.netbanking.NonSeamlessNetBankingChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.AbstractUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.NonSeamlessUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.SeamlessUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.wallet.AbstractWalletChargingResponse;
import in.wynk.payment.dto.response.presentation.wallet.NonSeamlessWalletChargingResponse;
import in.wynk.payment.dto.response.presentation.wallet.SeamlessWalletChargingResponse;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentConstants.*;

/**
 * @author Nishesh Pandey
 */
@Component
@RequiredArgsConstructor
public class PaymentChargingPresentation implements IPaymentPresentation<AbstractChargingResponse, ChargingResponseWrapper<?>> {

    @Value("${payment.encKey}")
    private String encryptionKey;

    private final Gson gson;
    private final PaymentCachingService paymentCache;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IEntityCacheService<ClientDetails, String> clientCache;
    private final Map<String, IPaymentPresentation<? extends AbstractChargingResponse, ChargingResponseWrapper<?>>> delegate = new HashMap<>();

    @PostConstruct
    public void init() {
        delegate.put("UPI", new UpiChargingPresentation());
        delegate.put("CARD", new CardChargingPresentation());
        delegate.put("WALLET", new WalletChargingPresentation());
        delegate.put("NET_BANKING", new NetBankingChargingPresentation());
    }

    @Override
    public WynkResponseEntity<AbstractChargingResponse> transform(ChargingResponseWrapper<?> payload) {
        final PaymentMethod method = paymentMethodCachingService.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
        return (WynkResponseEntity<AbstractChargingResponse>) delegate.get(method.getGroup()).transform(payload);
    }

    @SneakyThrows
    private String handleFormSpec(ChargingResponseWrapper<?> payload) {
        final IPostFormSpec<?, ?> formSpec = (IPostFormSpec<?,?>) payload.getPgResponse();
        return EncryptionUtils.encrypt(encryptionKey, gson.toJson(formSpec.getForm()));
    }


    private class UpiChargingPresentation implements IPaymentPresentation<AbstractUpiChargingResponse, ChargingResponseWrapper<?>> {

        private final Map<String, IPaymentPresentation<? extends AbstractUpiChargingResponse, ChargingResponseWrapper<?>>> delegate = new HashMap<>();

        public UpiChargingPresentation() {
            delegate.put("INTENT", new Intent());
            delegate.put("COLLECT", new Collect());
        }

        @Override
        public WynkResponseEntity<AbstractUpiChargingResponse> transform(ChargingResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCachingService.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractUpiChargingResponse>) delegate.get(method.getFlowType()).transform(payload);
        }

        private class Intent implements IPaymentPresentation<SeamlessUpiChargingResponse, ChargingResponseWrapper<?>> {

            @SneakyThrows
            @Override
            public WynkResponseEntity<SeamlessUpiChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                final PaymentMethod method = paymentMethodCachingService.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
                final PlanDTO planToBePurchased = paymentCache.getPlan(payload.getTransaction().getPlanId());
                final OfferDTO offerToBePurchased = paymentCache.getOffer(planToBePurchased.getLinkedOfferId());
                final String prefix = (String) method.getMeta().getOrDefault(UPI_PREFIX, "upi");
                final IUpiIntentSpec intentSpec = (IUpiIntentSpec) payload.getPgResponse();
                final String stringBuilder = prefix +
                        ":" +
                        "//" +
                        "pay" +
                        "?pa=" + intentSpec.getPayeeVpa() +
                        "&pn=" + Optional.of(intentSpec.getPayeeDisplayName()).orElse(clientCache.get(payload.getTransaction().getClientAlias()).<String>getMeta(PN).orElse(DEFAULT_PN)) +
                        "&tr=" + intentSpec.getMerchantOrderID() +
                        "&am=" + intentSpec.getAmountToBePaid() +
                        "&cu=" + intentSpec.getCurrencyCode().orElse("INR") +
                        "&tn=" + intentSpec.getTransactionNote().orElse(offerToBePurchased.getTitle()) +
                        "&mc=" + MERCHANT_CODE +
                        "&tid=" + payload.getTransaction().getIdStr();
                final String form = EncryptionUtils.encrypt(encryptionKey, gson.toJson(stringBuilder));
                return WynkResponseEntity.<SeamlessUpiChargingResponse>builder().data(SeamlessUpiChargingResponse.builder().appPackage((String) method.getMeta().get(APP_PACKAGE)).deeplink(form).build()).build();
            }
        }

        private class Collect implements IPaymentPresentation<NonSeamlessUpiChargingResponse, ChargingResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessUpiChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessUpiChargingResponse>builder().data(NonSeamlessUpiChargingResponse.builder().form(encForm).build()).build();
            }
        }

    }

    private class NetBankingChargingPresentation implements IPaymentPresentation<AbstractNetBankingChargingResponse, ChargingResponseWrapper<?>> {

        private final Map<String, IPaymentPresentation<? extends AbstractNetBankingChargingResponse, ChargingResponseWrapper<?>>> delegate = new HashMap<>();

        public NetBankingChargingPresentation() {
            this.delegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public WynkResponseEntity<AbstractNetBankingChargingResponse> transform(ChargingResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCachingService.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractNetBankingChargingResponse>) delegate.get(method.getFlowType()).transform(payload);
        }

        public class NonSeamless implements IPaymentPresentation<NonSeamlessNetBankingChargingResponse, ChargingResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessNetBankingChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessNetBankingChargingResponse>builder().data(NonSeamlessNetBankingChargingResponse.builder().form(encForm).build()).build();
            }
        }
    }

    private class CardChargingPresentation implements IPaymentPresentation<AbstractCardChargingResponse, ChargingResponseWrapper<?>> {

        private final Map<String, IPaymentPresentation<? extends AbstractCardChargingResponse, ChargingResponseWrapper<?>>> delegate = new HashMap<>();

        public CardChargingPresentation() {
            delegate.put("SEAMLESS", new Seamless());
            delegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public WynkResponseEntity<AbstractCardChargingResponse> transform(ChargingResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCachingService.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractCardChargingResponse>) delegate.get(method.getFlowType()).transform(payload);
        }

        public class Seamless implements IPaymentPresentation<SeamlessCardChargingResponse, ChargingResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<SeamlessCardChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        public class NonSeamless implements IPaymentPresentation<NonSeamlessCardChargingResponse, ChargingResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessCardChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessCardChargingResponse>builder().data(NonSeamlessCardChargingResponse.builder().form(encForm).build()).build();
            }
        }

    }

    private class WalletChargingPresentation implements IPaymentPresentation<AbstractWalletChargingResponse, ChargingResponseWrapper<?>> {

        private final Map<String, IPaymentPresentation<? extends AbstractWalletChargingResponse, ChargingResponseWrapper<?>>> delegate = new HashMap<>();

        public WalletChargingPresentation() {
            delegate.put("SEAMLESS", new Seamless());
            delegate.put("NON_SEAMLESS", new NonSeamless());
        }

        @Override
        public WynkResponseEntity<AbstractWalletChargingResponse> transform(ChargingResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCachingService.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractWalletChargingResponse>) delegate.get(method.getFlowType()).transform(payload);
        }

        public class Seamless implements IPaymentPresentation<SeamlessWalletChargingResponse, ChargingResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<SeamlessWalletChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        public class NonSeamless implements IPaymentPresentation<NonSeamlessWalletChargingResponse, ChargingResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessWalletChargingResponse> transform(ChargingResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessWalletChargingResponse>builder().data(NonSeamlessWalletChargingResponse.builder().form(encForm).build()).build();
            }
        }

    }

}
