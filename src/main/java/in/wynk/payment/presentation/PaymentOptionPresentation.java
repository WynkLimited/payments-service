package in.wynk.payment.presentation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.service.PaymentGroupCachingService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.PaymentGroupsDTO;
import in.wynk.payment.dto.response.card.Card;
import in.wynk.payment.dto.response.netbanking.NetBanking;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaymentOptionPresentation implements IWynkPresentation<PaymentOptionsDTO, Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

    private final PaymentCachingService payCache;
    private final PaymentGroupCachingService groupCache;
    private final PaymentMethodCachingService methodCache;

    @Override
    public PaymentOptionsDTO transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
        IPaymentOptionsRequest request = payload.getFirst();
        FilteredPaymentOptionsResult result = payload.getSecond();
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder<?,?> builder = PaymentOptionsDTO.builder();
        if (request.getProductDetails().getType().equalsIgnoreCase(BaseConstants.PLAN)) builder.productDetails(buildPlanDetails(request.getProductDetails().getId(), result.isTrialEligible()));
        else builder.productDetails(buildPointDetails(request.getProductDetails().getId()));
        final Set<String> uniqueGroupIds = result.getMethods().stream().map(in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO::getGroup).collect(Collectors.toSet());
        builder.paymentGroups(uniqueGroupIds.stream().filter(groupCache::containsKey).map(groupCache::get).sorted(Comparator.comparingInt(PaymentGroup::getHierarchy)).map(group -> PaymentGroupsDTO.builder().id(group.getId()).title(group.getDisplayName()).description(group.getDescription()).build()).collect(Collectors.toList()));
    }

    private class PaymentMethodPresentation implements IPresentation<Map<String, List<AbstractPaymentMethodDTO>>,Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

        @Override
        public Map<String, List<AbstractPaymentMethodDTO>> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
            return null;
        }

        private class UPIPresentation implements IPresentation<List<UPI>,Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

            @Override
            public List<UPI> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
                return null;
            }

        }

        private class CardPresentation implements IPresentation<List<Card>,Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

            @Override
            public List<Card> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
                return null;
            }

        }

        private class WalletPresentation implements IPresentation<List<Wallet>,Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

            @Override
            public List<Wallet> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
                return null;
            }

        }

        private class NetBankingPresentation implements IPresentation<List<NetBanking>,Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult>> {

            @Override
            public List<NetBanking> transform(Pair<IPaymentOptionsRequest, FilteredPaymentOptionsResult> payload) {
                return null;
            }

        }


    }



    private void addPaymentMethod(PaymentMethod paymentMethod, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, Supplier<Boolean> autoRenewalSupplier) {
        String group = paymentMethod.getGroup();
        List<AbstractPaymentOptions> payOptions = new ArrayList<>();
        //if APS, check if it comes into eligible methods and update other details required for UI as well
        if (AIRTEL_PAY_STACK.equals(paymentMethod.getPaymentCode().getCode())) {
            payOptions.forEach(payOption -> {
                if (common.isGroupEligible(payOption.getType(), group)) {
                    switch (group) {
                        case UPI:
                            UpiPaymentOptions upiPaymentOptions = (UpiPaymentOptions) payOption;
                            upiPaymentOptions.getUpiSupportedApps().forEach(upiSupportedApps -> {
                                if (upiSupportedApps.getUpiPspAppName().equalsIgnoreCase((String) paymentMethod.getMeta().get(APP_NAME))) {
                                    addUpiDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, upiSupportedApps.getHealth());
                                }
                            });
                            break;
                        case CARD:
                            addCardDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier);
                            break;
                        case NET_BANKING:
                            NetBankingPaymentOptions netBankingPaymentOptions = (NetBankingPaymentOptions) payOption;
                            netBankingPaymentOptions.getSubOption().forEach(netBankingSubOptions -> {
                                if (netBankingSubOptions.getSubType().equalsIgnoreCase((String) paymentMethod.getMeta().get("bankCode"))) {
                                    addNetBankingDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, netBankingSubOptions.getHealth());
                                }
                            });
                            break;
                        case WALLET:
                            WalletPaymentsOptions walletPaymentsOptions = (WalletPaymentsOptions) payOption;
                            //TODO: update code for wallet from APS
                            walletPaymentsOptions.getWalletSubOption().forEach(walletSubOption -> {
                                if (walletSubOption.getSubType().equalsIgnoreCase(paymentMethod.getSubtitle())) {
                                    addNetWalletDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, walletSubOption.getHealth());
                                }
                            });
                            break;
                    }
                }
            });
        } else {
            addEligiblePaymentMethodForPayU(group, paymentMethodDTO, paymentMethod, autoRenewalSupplier);
        }

    }

    private void addEligiblePaymentMethodForPayU(String group, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier) {
        switch (group) {
            case UPI:
                addUpiDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, null);
                break;
            case CARD:
                addCardDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier);
                break;
            case NET_BANKING:
                addNetBankingDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, null);
                break;
            case WALLET:
                addNetWalletDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, null);
                break;

            case ADDTOBILL:
                if (Objects.isNull(paymentMethodDTO.getAddToBills())) {
                    paymentMethodDTO.setAddToBills(new ArrayList<>());
                }
                final String description =
                        Objects.nonNull(paymentMethod.getMeta()) && Objects.nonNull(paymentMethod.getMeta().get(META_DESCRIPTION)) ? (String) paymentMethod.getMeta().get(META_DESCRIPTION) : null;
                paymentMethodDTO.getAddToBills()
                        .add(AddToBill.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build()).build());
                break;

            //TODO: Google Play Billing Phase 2
            /*case PaymentConstants.BILLING:
                if (Objects.isNull(paymentMethodDTO.getAddToBills())) {
                    paymentMethodDTO.setAddToBills(new ArrayList<>());
                }
                paymentMethodDTO.getAddToBills()
                        .add(AddToBill.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build())
                                .build());
                break;
            default:
                throw new WynkRuntimeException("Payment Method not supported");*/
        }

    }


    private void addNetWalletDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier,
                                     String health) {

        if (Objects.isNull(paymentMethodDTO.getWallet())) {
            paymentMethodDTO.setWallet(new ArrayList<>());
        }
        if (Objects.isNull(paymentMethod.getMeta())) {
            throw new WynkRuntimeException("Meta information is missing in payment method");
        }
        paymentMethodDTO.getWallet()
                .add(Wallet.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).health(health).description((String) paymentMethod.getMeta().get(META_DESCRIPTION))
                        .code(paymentMethod.getPaymentCode().getCode())
                        .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                        .supportingDetails(WalletCardSupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported())
                                .intentDetails(WalletCardSupportingDetails.IntentDetails.builder().packageName((String) paymentMethod.getMeta().get(META_PACKAGE_NAME)).build()).build())
                        .build());
    }

    private void addNetBankingDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier,
                                      String health) {
        if (Objects.isNull(paymentMethodDTO.getNetBanking())) {
            paymentMethodDTO.setNetBanking(new ArrayList<>());
        }
        final String description =
                Objects.nonNull(paymentMethod.getMeta()) && Objects.nonNull(paymentMethod.getMeta().get(META_DESCRIPTION)) ? (String) paymentMethod.getMeta().get(META_DESCRIPTION) : null;
        paymentMethodDTO.getNetBanking()
                .add(NetBanking.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).health(health).description(description).code(paymentMethod.getPaymentCode().getCode())
                        .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                        .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build())
                        .build());
    }

    private void addCardDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier) {
        if (Objects.isNull(paymentMethodDTO.getCard())) {
            paymentMethodDTO.setCard(new ArrayList<>());
        }
        Map<String, String> map = new HashMap<>();
        map.put("icon", paymentMethod.getIconUrl());
        map.putAll((Map<String, String>) paymentMethod.getMeta().get("card_type_to_icon_map"));
        final String description =
                Objects.nonNull(paymentMethod.getMeta()) && Objects.nonNull(paymentMethod.getMeta().get(META_DESCRIPTION)) ? (String) paymentMethod.getMeta().get(META_DESCRIPTION) : null;
        boolean saveSupported = Objects.nonNull(paymentMethod.getMeta().get(SAVE_SUPPORTED)) && (boolean) paymentMethod.getMeta().get(SAVE_SUPPORTED);
        paymentMethodDTO.getCard()
                .add(Card.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                        .uiDetails(map).supportingDetails(WalletCardSupportingDetails.builder().saveSupported(saveSupported).intentDetails(
                                WalletCardSupportingDetails.IntentDetails.builder().packageName((String) paymentMethod.getMeta().get(META_PACKAGE_NAME)).build()).build()).build());
    }

    private void addUpiDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier,
                               String health) {
        if (Objects.isNull(paymentMethod.getMeta())) {
            throw new WynkRuntimeException("Meta information is missing in payment method");
        }

        if (Objects.isNull(paymentMethodDTO.getUpi())) {
            paymentMethodDTO.setUpi(new ArrayList<>());
        }

        boolean saveSupported = Objects.nonNull(paymentMethod.getMeta().get(SAVE_SUPPORTED)) && (boolean) paymentMethod.getMeta().get(SAVE_SUPPORTED);
        paymentMethodDTO.getUpi()
                .add(in.wynk.payment.dto.response.upi.UPI.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).health(health)
                        .description((String) paymentMethod.getMeta().get(META_DESCRIPTION))
                        .code(paymentMethod.getPaymentCode().getCode()).appName((String) paymentMethod.getMeta().get(APP_NAME))
                        .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                        .supportingDetails(in.wynk.payment.dto.response.upi.UPI.UpiSupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported())
                                .suffixes(paymentMethod.getSuffixes()).paymentStatusPoll((Double) paymentMethod.getMeta().get("payment_status_poll")).saveSupported(saveSupported)
                                .paymentTimer((Double) paymentMethod.getMeta().get("payment_timer")).packageName((String) paymentMethod.getMeta().get(META_PACKAGE_NAME))
                                .intent((Boolean) paymentMethod.getMeta().get("intent"))
                                .buildCheck((Map<String, Map<String, Integer>>) paymentMethod.getMeta().get("build_check")).build())
                        .build());
    }

    private List<SavedPaymentDTO> addSavedPaymentOptions(List<AbstractSavedPayOptions> payOptions, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO) {
        List<SavedPaymentDTO> abstractSavedPaymentDTOList = new ArrayList<>();
        for (AbstractSavedPayOptions payOption : payOptions) {
            switch (payOption.getType()) {
                case UPI:
                    UpiSavedOptions upiDetails = (UpiSavedOptions) payOption;
                    List<in.wynk.payment.dto.response.upi.UPI> upiPayment =
                            paymentMethodDTO.getUpi().stream().filter(upi -> upi.getCode().equals(AIRTEL_PAY_STACK) && upi.getAppName().equals(upiDetails.getUpiApp())).collect(Collectors.toList());
                    if (upiPayment.size() > 0) {
                        abstractSavedPaymentDTOList.add(UpiSavedDetails.builder().id(upiPayment.get(0).getId()).code(AIRTEL_PAY_STACK).group(upiDetails.getType()).isFavorite(upiDetails.isFavourite())
                                .isRecommended(upiDetails.isPreferred()).health(upiDetails.getHealth()).vpa(upiDetails.getUserVPA()).build());
                    }

                    break;
                case CARDS:
                    CardSavedPayOptions cardDetails = (CardSavedPayOptions) payOption;
                    List<Card> cardPayment = paymentMethodDTO.getCard().stream().filter(card -> card.getCode().equals(AIRTEL_PAY_STACK)).collect(Collectors.toList());
                    if (cardPayment.size() > 0) {
                        abstractSavedPaymentDTOList.add(
                                CardSavedDetails.builder().id(cardPayment.get(0).getId()).code(AIRTEL_PAY_STACK).group(cardDetails.getType()).isFavorite(cardDetails.isFavourite())
                                        .isRecommended(cardDetails.isPreferred()).health(cardDetails.getHealth()).autoPayEnabled(cardDetails.isAutoPayEnable()).cardToken(cardDetails.getCardRefNo())
                                        .cardNumber(cardDetails.getCardNumber()).cardType(cardDetails.getCardType()).cardCategory(cardDetails.getCardCategory()).cardbin(cardDetails.getCardBin())
                                        .bankName(cardDetails.getCardBankName()).bankCode(cardDetails.getBankCode()).expiryMonth(cardDetails.getExpiryMonth()).expiryYear(cardDetails.getExpiryYear())
                                        .cvv(Integer.parseInt(cardDetails.getCvvLength())).icon(cardDetails.getIconUrl()).isFavorite(cardDetails.isFavourite()).isRecommended(cardDetails.isPreferred())
                                        .active(!cardDetails.isExpired() && !cardDetails.isBlocked()).build());
                    }
                    break;
                //TODo: Phase 2
                /*case "WALLETS":
                    WalletSavedOptions walletDetails = (WalletSavedOptions) payOption;
                    abstractSavedPaymentDTOList.add(
                            WalletSavedInfo.builder()*//*.id()*//*.code(AIRTEL_PAY_STACK).group(walletDetails.getType()).isFavorite(walletDetails.isFavourite()).isRecommended(walletDetails
                            .isPreferred())
                               .health(walletDetails.getHealth()).linked(walletDetails.isLinked()).valid(walletDetails.isValid()).canCheckOut(walletDetails.isShowOnQuickCheckout())
                                    .addMoneyAllowed(true).id(walletDetails.getWalletId()).balance(walletDetails.getWalletBalance())
                                    .minBalance(walletDetails.getMinAmount()).build());*/

            }
        }
        return abstractSavedPaymentDTOList;
    }

    private PaymentOptionsDTO.PlanDetails buildPlanDetails(String planId, boolean trialEligible) {
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

    private PaymentOptionsDTO.PointDetails buildPointDetails(String itemID) {
        final ItemDTO item = payCache.getItem(itemID);
        return PaymentOptionsDTO.PointDetails.builder()
                .id(item.getId())
                .title(item.getName())
                .price(item.getPrice())
                .build();
    }
}
