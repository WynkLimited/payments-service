package in.wynk.payment.presentation;

import com.google.gson.Gson;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.FlowType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.gateway.IPostFormSpec;
import in.wynk.payment.dto.gateway.IRedirectSpec;
import in.wynk.payment.dto.gateway.IUpiIntentSpec;
import in.wynk.payment.dto.manager.ChargingGatewayResponseWrapper;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.presentation.card.AbstractCardChargingResponse;
import in.wynk.payment.dto.response.presentation.card.NonSeamlessCardChargingResponse;
import in.wynk.payment.dto.response.presentation.card.RedirectCardChargingResponse;
import in.wynk.payment.dto.response.presentation.card.SeamlessCardChargingResponse;
import in.wynk.payment.dto.response.presentation.netbanking.AbstractNetBankingChargingResponse;
import in.wynk.payment.dto.response.presentation.netbanking.NonSeamlessNetBankingChargingResponse;
import in.wynk.payment.dto.response.presentation.netbanking.RedirectNetBankingChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.AbstractUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.NonSeamlessUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.RedirectUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.upi.SeamlessUpiChargingResponse;
import in.wynk.payment.dto.response.presentation.wallet.AbstractWalletChargingResponse;
import in.wynk.payment.dto.response.presentation.wallet.NonSeamlessWalletChargingResponse;
import in.wynk.payment.dto.response.presentation.wallet.RedirectWalletChargingResponse;
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

import static in.wynk.payment.constant.FlowType.UPI;
import static in.wynk.payment.constant.FlowType.*;
import static in.wynk.payment.constant.UpiConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.APP_PACKAGE;
import static in.wynk.payment.core.constant.PaymentConstants.DEFAULT_PN;

/**
 * @author Nishesh Pandey
 */
@Component
@RequiredArgsConstructor
public class PaymentChargingPresentation implements IPaymentPresentation<AbstractChargingResponse, ChargingGatewayResponseWrapper<?>> {

    @Value("${payment.encKey}")
    private String encryptionKey;

    private final Gson gson;
    private final PaymentCachingService paymentCache;
    private final IEntityCacheService<ClientDetails, String> clientCache;
    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;
    private final Map<FlowType, IPaymentPresentation<? extends AbstractChargingResponse, ChargingGatewayResponseWrapper<?>>> delegate = new HashMap<>();

    @PostConstruct
    public void init () {
        delegate.put(UPI, new UpiChargingPresentation());
        delegate.put(CARD, new CardChargingPresentation());
        delegate.put(WALLET, new WalletChargingPresentation());
        delegate.put(NET_BANKING, new NetBankingChargingPresentation());
    }

    @SneakyThrows
    @Override
    public WynkResponseEntity<AbstractChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
        final PaymentMethod method = paymentMethodCache.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
        return (WynkResponseEntity<AbstractChargingResponse>) delegate.get(FlowType.valueOf(method.getGroup())).transform(payload);
    }

    @SneakyThrows
    private String handleFormSpec (ChargingGatewayResponseWrapper<?> payload) {
        final IPostFormSpec<?, ?> formSpec = (IPostFormSpec<?, ?>) payload.getPgResponse();
        return EncryptionUtils.encrypt(encryptionKey, gson.toJson(formSpec.getForm()));
    }


    private class UpiChargingPresentation implements IPaymentPresentation<AbstractUpiChargingResponse, ChargingGatewayResponseWrapper<?>> {

        private final Map<FlowType, IPaymentPresentation<? extends AbstractUpiChargingResponse, ChargingGatewayResponseWrapper<?>>> delegate = new HashMap<>();

        public UpiChargingPresentation () {
            delegate.put(SEAMLESS, new Seamless());
            delegate.put(NON_SEAMLESS, new NonSeamless());
            delegate.put(NON_SEAMLESS_REDIRECT_FLOW, new Redirect());
        }

        @SneakyThrows
        @Override
        public WynkResponseEntity<AbstractUpiChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractUpiChargingResponse>) delegate.get(FlowType.valueOf(method.getFlowType())).transform(payload);
        }

        private class Seamless implements IPaymentPresentation<SeamlessUpiChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @SneakyThrows
            @Override
            public WynkResponseEntity<SeamlessUpiChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final PaymentMethod method = paymentMethodCache.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
                final PlanDTO planToBePurchased = paymentCache.getPlan(payload.getTransaction().getPlanId());
                final OfferDTO offerToBePurchased = paymentCache.getOffer(planToBePurchased.getLinkedOfferId());
                final String prefix = (String) method.getMeta().getOrDefault(UPI_PREFIX, "upi");
                final IUpiIntentSpec intentSpec = (IUpiIntentSpec) payload.getPgResponse();
                final String stringBuilder = prefix +
                        ":" +
                        "//" +
                        "pay" +
                        "?pa=" + intentSpec.getPayeeVpa() +
                        "&pn=" + Optional.of(intentSpec.getPayeeDisplayName())
                        .orElse(clientCache.get(payload.getTransaction().getClientAlias()).<String>getMeta(UPI_PAYEE_NAME).orElse(DEFAULT_PN)) +
                        "&tr=" + intentSpec.getMerchantOrderID() +
                        "&am=" + intentSpec.getAmountToBePaid() +
                        "&cu=" + intentSpec.getCurrencyCode().orElse("INR") +
                        "&tn=" + intentSpec.getTransactionNote().orElse(offerToBePurchased.getTitle()) +
                        "&mc=" + UPI_MERCHANT_CODE +
                        "&tid=" + payload.getTransaction().getIdStr();
                final String form = EncryptionUtils.encrypt(encryptionKey, gson.toJson(stringBuilder));
                return WynkResponseEntity.<SeamlessUpiChargingResponse>builder()
                        .data(SeamlessUpiChargingResponse.builder().appPackage((String) method.getMeta().get(APP_PACKAGE)).deeplink(form).build()).build();
            }
        }

        private class NonSeamless implements IPaymentPresentation<NonSeamlessUpiChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessUpiChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessUpiChargingResponse>builder().data(NonSeamlessUpiChargingResponse.builder().form(encForm).build()).build();
            }

        }

        private class Redirect implements IPaymentPresentation<RedirectUpiChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<RedirectUpiChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final IRedirectSpec<String> redirectSpec = (IRedirectSpec<String>) payload.getPgResponse();
                return WynkResponseEntity.<RedirectUpiChargingResponse>builder().data(RedirectUpiChargingResponse.builder().redirectUrl(redirectSpec.getRedirectUrl()).build()).build();
            }
        }

    }

    private class NetBankingChargingPresentation implements IPaymentPresentation<AbstractNetBankingChargingResponse, ChargingGatewayResponseWrapper<?>> {

        private final Map<FlowType, IPaymentPresentation<? extends AbstractNetBankingChargingResponse, ChargingGatewayResponseWrapper<?>>> delegate = new HashMap<>();

        public NetBankingChargingPresentation () {
            delegate.put(NON_SEAMLESS, new NonSeamless());
            delegate.put(NON_SEAMLESS_REDIRECT_FLOW, new Redirect());
        }

        @SneakyThrows
        @Override
        public WynkResponseEntity<AbstractNetBankingChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractNetBankingChargingResponse>) delegate.get(FlowType.valueOf(method.getFlowType())).transform(payload);
        }

        public class NonSeamless implements IPaymentPresentation<NonSeamlessNetBankingChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessNetBankingChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessNetBankingChargingResponse>builder().data(NonSeamlessNetBankingChargingResponse.builder().form(encForm).build()).build();
            }
        }

        private class Redirect implements IPaymentPresentation<RedirectNetBankingChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<RedirectNetBankingChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final IRedirectSpec<String> redirectSpec = (IRedirectSpec<String>) payload.getPgResponse();
                return WynkResponseEntity.<RedirectNetBankingChargingResponse>builder().data(RedirectNetBankingChargingResponse.builder().redirectUrl(redirectSpec.getRedirectUrl()).build()).build();
            }
        }

    }

    private class CardChargingPresentation implements IPaymentPresentation<AbstractCardChargingResponse, ChargingGatewayResponseWrapper<?>> {

        private final Map<FlowType, IPaymentPresentation<? extends AbstractCardChargingResponse, ChargingGatewayResponseWrapper<?>>> delegate = new HashMap<>();

        public CardChargingPresentation () {
            delegate.put(SEAMLESS, new Seamless());
            delegate.put(NON_SEAMLESS, new NonSeamless());
            delegate.put(NON_SEAMLESS_REDIRECT_FLOW, new Redirect());
        }

        @SneakyThrows
        @Override
        public WynkResponseEntity<AbstractCardChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractCardChargingResponse>) delegate.get(FlowType.valueOf(method.getFlowType())).transform(payload);
        }

        public class Seamless implements IPaymentPresentation<SeamlessCardChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<SeamlessCardChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        public class NonSeamless implements IPaymentPresentation<NonSeamlessCardChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessCardChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessCardChargingResponse>builder().data(NonSeamlessCardChargingResponse.builder().form(encForm).build()).build();
            }
        }

        private class Redirect implements IPaymentPresentation<RedirectCardChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<RedirectCardChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final IRedirectSpec<String> redirectSpec = (IRedirectSpec<String>) payload.getPgResponse();
                return WynkResponseEntity.<RedirectCardChargingResponse>builder().data(RedirectCardChargingResponse.builder().redirectUrl(redirectSpec.getRedirectUrl()).build()).build();
            }
        }

    }

    private class WalletChargingPresentation implements IPaymentPresentation<AbstractWalletChargingResponse, ChargingGatewayResponseWrapper<?>> {

        private final Map<FlowType, IPaymentPresentation<? extends AbstractWalletChargingResponse, ChargingGatewayResponseWrapper<?>>> delegate = new HashMap<>();

        public WalletChargingPresentation () {
            delegate.put(SEAMLESS, new Seamless());
            delegate.put(NON_SEAMLESS, new NonSeamless());
            delegate.put(NON_SEAMLESS_REDIRECT_FLOW, new Redirect());
        }

        @SneakyThrows
        @Override
        public WynkResponseEntity<AbstractWalletChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
            final PaymentMethod method = paymentMethodCache.get(payload.getPurchaseDetails().getPaymentDetails().getPaymentId());
            return (WynkResponseEntity<AbstractWalletChargingResponse>) delegate.get(FlowType.valueOf(method.getFlowType())).transform(payload);
        }

        public class Seamless implements IPaymentPresentation<SeamlessWalletChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<SeamlessWalletChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

        public class NonSeamless implements IPaymentPresentation<NonSeamlessWalletChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<NonSeamlessWalletChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final String encForm = PaymentChargingPresentation.this.handleFormSpec(payload);
                return WynkResponseEntity.<NonSeamlessWalletChargingResponse>builder().data(NonSeamlessWalletChargingResponse.builder().form(encForm).build()).build();
            }
        }

        private class Redirect implements IPaymentPresentation<RedirectWalletChargingResponse, ChargingGatewayResponseWrapper<?>> {

            @Override
            public WynkResponseEntity<RedirectWalletChargingResponse> transform (ChargingGatewayResponseWrapper<?> payload) {
                final IRedirectSpec<String> redirectSpec = (IRedirectSpec<String>) payload.getPgResponse();
                return WynkResponseEntity.<RedirectWalletChargingResponse>builder().data(RedirectWalletChargingResponse.builder().redirectUrl(redirectSpec.getRedirectUrl()).build()).build();
            }
        }

    }

}
