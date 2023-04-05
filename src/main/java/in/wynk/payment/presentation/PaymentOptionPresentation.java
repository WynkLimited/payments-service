package in.wynk.payment.presentation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.payment.constant.CardConstants;
import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.constant.WalletConstants;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.service.PaymentGroupCachingService;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.addtobill.AddToBillConstants;
import in.wynk.payment.dto.aps.common.HealthStatus;
import in.wynk.payment.dto.common.*;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.PaymentGroupsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.dto.response.SupportingDetails;
import in.wynk.payment.dto.response.UiDetails;
import in.wynk.payment.dto.response.billing.AddToBill;
import in.wynk.payment.dto.response.card.Card;
import in.wynk.payment.dto.response.netbanking.NetBanking;
import in.wynk.payment.dto.response.paymentoption.*;
import in.wynk.payment.dto.response.upi.UPI;
import in.wynk.payment.dto.response.wallet.Wallet;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaymentOptionPresentation implements IWynkPresentation<PaymentOptionsDTO, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

    private static final String BUILD_CHECK = "build_check";
    private static final String SAVE_SUPPORTED = "saveSupported";
    private static final String POLLING_TIMER = "payment_timer";
    private static final String META_PACKAGE_NAME = "package_name";
    private static final String POLLING_FREQUENCY = "payment_status_poll";


    private final PaymentCachingService payCache;
    private final PaymentGroupCachingService groupCache;

    private final IPresentation<IProductDetails, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> productPresentation = new ProductPresentation();
    private final IPresentation<List<AbstractSavedPaymentDTO>, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> detailsPresentation = new SavedDetailsPresentation();
    private final IPresentation<Map<String, List<AbstractPaymentMethodDTO>>, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> methodPresentation = new PaymentMethodPresentation();

    @Override
    public PaymentOptionsDTO transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
        final FilteredPaymentOptionsResult result = payload.getSecond();
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder<?, ?> builder = PaymentOptionsDTO.builder().productDetails(productPresentation.transform(payload));
        final Set<String> uniqueGroupIds = result.getMethods().stream().map(PaymentMethodDTO::getGroup).collect(Collectors.toSet());
        builder.paymentGroups(uniqueGroupIds.stream().filter(groupCache::containsKey).map(groupCache::get).sorted(Comparator.comparingInt(PaymentGroup::getHierarchy)).map(group -> PaymentGroupsDTO.builder().id(group.getId()).title(group.getDisplayName()).description(group.getDescription()).build()).collect(Collectors.toList()));
        return builder.paymentMethods(methodPresentation.transform(payload)).savedPaymentDTO(detailsPresentation.transform(payload)).build();
    }

    private class ProductPresentation implements IPresentation<IProductDetails, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {
        private final Map<String, IPresentation<? extends IProductDetails, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>>> delegate = new HashMap<>();

        public ProductPresentation() {
            delegate.put(BaseConstants.PLAN, new PlanProductPresentation());
            delegate.put(BaseConstants.POINT, new ItemProductPresentation());
        }

        @Override
        public IProductDetails transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
            return delegate.get(payload.getFirst().getProductDetails().getType()).transform(payload);
        }

        private class PlanProductPresentation implements IPresentation<PaymentOptionsDTO.PlanDetails, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

            @Override
            public PaymentOptionsDTO.PlanDetails transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
                final boolean trialEligible = payload.getSecond().isTrialEligible();
                final String planId = payload.getFirst().getProductDetails().getId();
                PlanDTO plan = payCache.getPlan(planId);
                OfferDTO offer = payCache.getOffer(plan.getLinkedOfferId());
                PartnerDTO partner = payCache.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : BaseConstants.DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
                PaymentOptionsDTO.PlanDetails.PlanDetailsBuilder<?, ?> planDetailsBuilder =
                        PaymentOptionsDTO.PlanDetails.builder().id(planId).validityUnit(plan.getPeriod().getValidityUnit()).perMonthValue((int) plan.getPrice().getMonthlyAmount())
                                .discountedPrice(plan.getPrice().getAmount()).price((int) plan.getPrice().getDisplayAmount()).discount(plan.getPrice().getSavings()).partnerLogo(partner.getPartnerLogo())
                                .month(plan.getPeriod().getMonth()).freeTrialAvailable(trialEligible).partnerName(partner.getName()).dailyAmount(plan.getPrice().getDailyAmount())
                                .currency(plan.getPrice().getCurrency()).title(offer.getTitle()).day(plan.getPeriod().getDay()).sku(plan.getSku()).subType(plan.getPlanType().getValue());
                if (trialEligible) {
                    final PlanDTO trialPlan = payCache.getPlan(plan.getLinkedFreePlanId());
                    planDetailsBuilder.trialDetails(
                            PaymentOptionsDTO.TrialPlanDetails.builder().id(String.valueOf(trialPlan.getId())).day(trialPlan.getPeriod().getDay()).month(trialPlan.getPeriod().getMonth())
                                    .validityUnit(trialPlan.getPeriod().getValidityUnit()).validity(trialPlan.getPeriod().getValidity()).currency(trialPlan.getPrice().getCurrency())
                                    .timeUnit(trialPlan.getPeriod().getTimeUnit()).build());
                }
                return planDetailsBuilder.build();
            }
        }

        private class ItemProductPresentation implements IPresentation<PaymentOptionsDTO.PointDetails, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

            @Override
            public PaymentOptionsDTO.PointDetails transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
                final ItemDTO item = payCache.getItem(payload.getFirst().getProductDetails().getId());
                return PaymentOptionsDTO.PointDetails.builder()
                        .id(item.getId())
                        .title(item.getName())
                        .price(item.getPrice())
                        .build();
            }
        }
    }

    private interface IPaymentOptionInfoPresentation<T extends AbstractPaymentMethodDTO, P extends AbstractPaymentOptionInfo> extends IPresentation<T, Pair<PaymentMethodDTO, Optional<P>>> {
    }

    private static class PaymentMethodPresentation implements IPresentation<Map<String, List<AbstractPaymentMethodDTO>>, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

        private final Map<String, IPaymentOptionInfoPresentation> delegate = new HashMap<>();

        public PaymentMethodPresentation() {
            delegate.put(UpiConstants.UPI, new UPIPresentation());
            delegate.put(CardConstants.CARD, new CardPresentation());
            delegate.put(WalletConstants.WALLET, new WalletPresentation());
            delegate.put(AddToBillConstants.ADDTOBILL, new BillingPresentation());
            delegate.put(NetBankingConstants.NET_BANKING, new NetBankingPresentation());
        }

        @Override
        public Map<String, List<AbstractPaymentMethodDTO>> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
            final Map<String, List<AbstractPaymentMethodDTO>> payMap = new HashMap<>();
            final List<PaymentMethodDTO> filteredMethods = payload.getSecond().getMethods();
            final Map<String, AbstractPaymentOptionInfo> optionInfoMap = payload.getSecond().getEligibilityRequest().getPayInstrumentProxyMap().values().stream().map(proxy -> proxy.getPaymentInstruments(payload.getFirst().getUserDetails().getMsisdn())).flatMap(Collection::stream).collect(Collectors.toMap(AbstractPaymentOptionInfo::getId, Function.identity(), (k1, k2) -> k1));
            filteredMethods.forEach(method -> {
                if (!payMap.containsKey(method.getGroup())) payMap.put(method.getGroup(), new ArrayList<>());
                payMap.get(method.getGroup()).add((AbstractPaymentMethodDTO) delegate.get(method.getGroup()).transform(Pair.of(method, Optional.ofNullable(optionInfoMap.get(method.getId())))));
            });
            return payMap;
        }

        private static class UPIPresentation implements IPaymentOptionInfoPresentation<UPI, UpiOptionInfo> {
            private static final String INTENT_SUPPORT = "intent";
            @Override
            public UPI transform(Pair<PaymentMethodDTO, Optional<UpiOptionInfo>> payload) {
                final PaymentMethodDTO methodDTO = payload.getFirst();
                final Optional<UpiOptionInfo> payOptional = payload.getSecond();
                return UPI.builder()
                        .id(methodDTO.getId())
                        .code(methodDTO.getPaymentCode())
                        .health(payOptional.map(AbstractPaymentOptionInfo::getHealth).orElse(HealthStatus.UP.name()))
                        .title(methodDTO.getDisplayName())
                        .description(methodDTO.getSubtitle())
                        .group(methodDTO.getGroup())
                        .description(methodDTO.getSubtitle())
                        .uiDetails(UiDetails.builder()
                                .icon(methodDTO.getIconUrl())
                                .build())
                        .supportingDetails(UPI.UpiSupportingDetails.builder()
                                .suffixes(methodDTO.getSuffixes())
                                .autoRenewSupported(methodDTO.isAutoRenewSupported())
                                .intent((Boolean) methodDTO.getMeta().get(INTENT_SUPPORT))
                                .paymentTimer((Double) methodDTO.getMeta().get(POLLING_TIMER))
                                .paymentStatusPoll((Double) methodDTO.getMeta().get(POLLING_FREQUENCY))
                                .buildCheck((Map<String, Map<String, Integer>>) methodDTO.getMeta().get(BUILD_CHECK))
                                .saveSupported(Objects.nonNull(methodDTO.getMeta().get(SAVE_SUPPORTED)) && (boolean) methodDTO.getMeta().get(SAVE_SUPPORTED))
                                .packageName((String) methodDTO.getMeta().getOrDefault(META_PACKAGE_NAME, payOptional.map(info -> info.getPackageId()).orElse(null)))
                                .build())
                        .build();
            }
        }

        private static class CardPresentation implements IPaymentOptionInfoPresentation<Card, CardOptionInfo> {
            private static final String SUPPORTED_CARD_ICONS = "supported_card_icons";
            @Override
            public Card transform(Pair<PaymentMethodDTO, Optional<CardOptionInfo>> payload) {
                final PaymentMethodDTO methodDTO = payload.getFirst();
                final Optional<CardOptionInfo> payOptional = payload.getSecond();
                return Card.builder()
                        .id(methodDTO.getId())
                        .code(methodDTO.getPaymentCode())
                        .description(methodDTO.getSubtitle())
                        .health(payOptional.map(AbstractPaymentOptionInfo::getHealth).orElse(HealthStatus.UP.name()))
                        .title(methodDTO.getDisplayName())
                        .group(methodDTO.getGroup())
                        .description(methodDTO.getSubtitle())
                        .uiDetails(Card.CardUiDetails.builder()
                                .icon(methodDTO.getIconUrl())
                                .supportedCardIcons((Map<String, String>) methodDTO.getMeta().get(SUPPORTED_CARD_ICONS))
                                .build())
                        .supportingDetails(SupportingDetails.builder()
                                .autoRenewSupported(methodDTO.isAutoRenewSupported())
                                .saveSupported(Objects.nonNull(methodDTO.getMeta().get(SAVE_SUPPORTED)) && (boolean) methodDTO.getMeta().get(SAVE_SUPPORTED))
                                .build())
                        .build();
            }

        }

        private static class WalletPresentation implements IPaymentOptionInfoPresentation<Wallet, WalletOptionInfo> {

            @Override
            public Wallet transform(Pair<PaymentMethodDTO, Optional<WalletOptionInfo>> payload) {
                final PaymentMethodDTO methodDTO = payload.getFirst();
                final Optional<WalletOptionInfo> payOptional = payload.getSecond();
                return Wallet.builder()
                        .id(methodDTO.getId())
                        .code(methodDTO.getPaymentCode())
                        .description(methodDTO.getSubtitle())
                        .health(payOptional.map(AbstractPaymentOptionInfo::getHealth).orElse(HealthStatus.UP.name()))
                        .title(methodDTO.getDisplayName())
                        .group(methodDTO.getGroup())
                        .description(methodDTO.getSubtitle())
                        .uiDetails(UiDetails.builder()
                                .icon(methodDTO.getIconUrl())
                                .build())
                        .supportingDetails(SupportingDetails.builder()
                                .autoRenewSupported(methodDTO.isAutoRenewSupported())
                                .saveSupported(Objects.nonNull(methodDTO.getMeta().get(SAVE_SUPPORTED)) && (boolean) methodDTO.getMeta().get(SAVE_SUPPORTED))
                                .build())
                        .build();
            }

        }

        private static class NetBankingPresentation implements IPaymentOptionInfoPresentation<NetBanking, NetBankingOptionInfo> {
            @Override
            public NetBanking transform(Pair<PaymentMethodDTO, Optional<NetBankingOptionInfo>> payload) {
                final PaymentMethodDTO methodDTO = payload.getFirst();
                final Optional<NetBankingOptionInfo> payOptional = payload.getSecond();
                return NetBanking.builder()
                        .id(methodDTO.getId())
                        .code(methodDTO.getPaymentCode())
                        .description(methodDTO.getSubtitle())
                        .health(payOptional.map(AbstractPaymentOptionInfo::getHealth).orElse(HealthStatus.UP.name()))
                        .title(methodDTO.getDisplayName())
                        .group(methodDTO.getGroup())
                        .description(methodDTO.getSubtitle())
                        .uiDetails(UiDetails.builder()
                                .icon(methodDTO.getIconUrl())
                                .build())
                        .supportingDetails(SupportingDetails.builder()
                                .autoRenewSupported(methodDTO.isAutoRenewSupported())
                                .saveSupported(Objects.nonNull(methodDTO.getMeta().get(SAVE_SUPPORTED)) && (boolean) methodDTO.getMeta().get(SAVE_SUPPORTED))
                                .build())
                        .build();
            }

        }

        private static class BillingPresentation implements IPaymentOptionInfoPresentation<AddToBill, BillingOptionInfo> {

            @Override
            public AddToBill transform(Pair<PaymentMethodDTO, Optional<BillingOptionInfo>> payload) {
                final PaymentMethodDTO methodDTO = payload.getFirst();
                final Optional<BillingOptionInfo> payOptional = payload.getSecond();
                return AddToBill.builder()
                        .id(methodDTO.getId())
                        .code(methodDTO.getPaymentCode())
                        .description(methodDTO.getSubtitle())
                        .health(payOptional.map(AbstractPaymentOptionInfo::getHealth).orElse(HealthStatus.UP.name()))
                        .title(methodDTO.getDisplayName())
                        .group(methodDTO.getGroup())
                        .description(methodDTO.getSubtitle())
                        .uiDetails(UiDetails.builder()
                                .icon(methodDTO.getIconUrl())
                                .build())
                        .supportingDetails(SupportingDetails.builder()
                                .autoRenewSupported(methodDTO.isAutoRenewSupported())
                                .saveSupported(Objects.nonNull(methodDTO.getMeta().get(SAVE_SUPPORTED)) && (boolean) methodDTO.getMeta().get(SAVE_SUPPORTED))
                                .build())
                        .build();
            }
        }

    }

    private interface ISavedDetailsPresentation<R extends AbstractSavedPaymentDTO, T extends AbstractSavedInstrumentInfo> extends IPresentation<R, T> {}

    private static class SavedDetailsPresentation implements IPresentation<List<AbstractSavedPaymentDTO>, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

        private final Map<String, ISavedDetailsPresentation> delegate = new HashMap<>();

        public SavedDetailsPresentation() {
            delegate.put(UpiConstants.UPI, new UPIPresentation());
            delegate.put(CardConstants.CARD, new CardPresentation());
            delegate.put(WalletConstants.WALLET, new WalletPresentation());
            delegate.put(AddToBillConstants.ADDTOBILL, new BillingPresentation());
            delegate.put(NetBankingConstants.NET_BANKING, new NetBankingPresentation());
        }

        @Override
        public List<AbstractSavedPaymentDTO> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
            return payload.getSecond().getEligibilityRequest().getPayInstrumentProxyMap().values().stream().filter(Objects::nonNull).flatMap(proxy -> proxy.getSavedDetails(payload.getSecond().getEligibilityRequest().getMsisdn()).stream()).map(details -> ((AbstractSavedPaymentDTO) delegate.get(details.getType()).transform(details))).collect(Collectors.toList());
        }

        private static class UPIPresentation implements ISavedDetailsPresentation<UpiSavedDetails, UpiSavedInfo> {
            @Override
            public UpiSavedDetails transform(UpiSavedInfo payload) {

                return UpiSavedDetails.builder()
                        .vpa(payload.getVpa())
                        .id(payload.getPayId())
                        .code(payload.getCode())
                        .group(payload.getGroup())
                        .health(payload.getHealth())
                        .favorite(payload.isFavourite())
                        .preferred(payload.isPreferred())
                        .packageName(payload.getPackageId())
                        .recommended(payload.isRecommended())
                        .autoPayEnabled(payload.isAutoPayEnabled())
                        .build();
            }

        }

        private static class CardPresentation implements ISavedDetailsPresentation<CardSavedDetails, SavedCardInfo> {
            @Override
            public CardSavedDetails transform(SavedCardInfo payload) {
                return CardSavedDetails.builder()
                        .id(payload.getId())
                        .code(payload.getCode())
                        .group(payload.getGroup())
                        .health(payload.getHealth())
                        .favorite(payload.isFavourite())
                        .preferred(payload.isPreferred())
                        .recommended(payload.isRecommended())
                        .autoPayEnabled(payload.isAutoPayEnabled())
                        .expressCheckout(payload.isExpressCheckout())
                        .cardToken(payload.getCardRefNo())
                        .cardNumber(payload.getMaskedCardNumber())
                        .bankName(payload.getCardBankName())
                        .expiryMonth(payload.getExpiryMonth())
                        .expiryYear(payload.getExpiryYear())
                        .cardType(payload.getCardType())
                        .cardCategory(payload.getCardCategory())
                        .cardbin(payload.getCardBin())
                        .bankCode(payload.getBankCode())
                        .cvvLength(payload.getCvvLength())
                        .icon(payload.getIconUrl())
                        .active(payload.isEnable())
                        .expired(payload.isExpired())
                        .blocked(payload.isBlocked())
                        .build();
            }

        }

        private static class WalletPresentation implements ISavedDetailsPresentation<WalletSavedDetails, WalletSavedInfo> {
            @Override
            public WalletSavedDetails transform(WalletSavedInfo payload) {
                return WalletSavedDetails.builder()
                        .id(payload.getId())
                        .code(payload.getCode())
                        .valid(payload.isValid())
                        .group(payload.getGroup())
                        .linked(payload.isLinked())
                        .health(payload.getHealth())
                        .balance(payload.getBalance())
                        .favorite(payload.isFavourite())
                        .walletId(payload.getWalletId())
                        .minBalance(payload.getMinBalance())
                        .preferred(payload.isPreferred())
                        .recommended(payload.isRecommended())
                        .autoPayEnabled(payload.isAutoPayEnabled())
                        .expressCheckout(payload.isExpressCheckout())
                        .addMoneyAllowed(payload.isAddMoneyAllowed())
                        .build();
            }

        }

        private static class NetBankingPresentation implements ISavedDetailsPresentation<NetBankingSavedDetails, NetBankingSavedInfo> {
            @Override
            public NetBankingSavedDetails transform(NetBankingSavedInfo payload) {
                return NetBankingSavedDetails.builder()
                        .id(payload.getId())
                        .code(payload.getCode())
                        .group(payload.getGroup())
                        .health(payload.getHealth())
                        .favorite(payload.isFavourite())
                        .preferred(payload.isPreferred())
                        .recommended(payload.isRecommended())
                        .autoPayEnabled(payload.isAutoPayEnabled())
                        .expressCheckout(payload.isExpressCheckout())
                        .build();
            }
        }

        private static class BillingPresentation implements ISavedDetailsPresentation<BillingSavedDetails, BillingSavedInfo> {
            @Override
            public BillingSavedDetails transform(BillingSavedInfo payload) {
                return BillingSavedDetails.builder()
                        .id(payload.getId())
                        .code(payload.getCode())
                        .group(payload.getGroup())
                        .health(payload.getHealth())
                        .favorite(payload.isFavourite())
                        .preferred(payload.isPreferred())
                        .linkedSis(payload.getLinkedSis())
                        .recommended(payload.isRecommended())
                        .autoPayEnabled(payload.isAutoPayEnabled())
                        .expressCheckout(payload.isExpressCheckout())
                        .build();
            }
        }
    }

}
