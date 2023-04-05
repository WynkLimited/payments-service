package in.wynk.payment.service.impl;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
import in.wynk.payment.dto.response.PaymentOptionsComputationResponse;
import in.wynk.payment.eligibility.request.PaymentOptionsComputationDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.service.IPaymentOptionComputationManager;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.common.constant.BaseConstants.POINT;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY023;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOptionServiceImplV2 implements IPaymentOptionServiceV2 {

    private final PaymentCachingService paymentCachingService;
    private final IPaymentOptionComputationManager<PaymentOptionsComputationResponse, PaymentOptionsEligibilityRequest> paymentOptionManager;
    private final SubscriptionServiceManagerImpl subscriptionServiceManager;

    @Override
    public FilteredPaymentOptionsResult getPaymentOptions(AbstractPaymentOptionsRequest<?> request) {
        if (request.getPaymentOptionRequest().getProductDetails().getType().equalsIgnoreCase(PLAN)) {
            return getPaymentOptionsDetailsForPlan(request.getPaymentOptionRequest());
        } else if (request.getPaymentOptionRequest().getProductDetails().getType().equalsIgnoreCase(POINT)) {
            return getPaymentOptionsDetailsForPoint(request.getPaymentOptionRequest());
        } else {
            log.info(PAYMENT_OPTIONS_FAILURE, "Either planId or itemId is mandatory for paymentOptions");
            throw new WynkRuntimeException(PAY023);
        }
    }

    private FilteredPaymentOptionsResult getPaymentOptionsDetailsForPlan(IPaymentOptionsRequest request) {
        boolean trialEligible = false;
        final PlanDTO paidPlan = paymentCachingService.getPlan(request.getProductDetails().getId());
        PaymentOptionsEligibilityRequest eligibilityRequest =
                PaymentOptionsEligibilityRequest.from(
                        PaymentOptionsComputationDTO.builder().planDTO(paidPlan).couponCode(request.getCouponId()).os(request.getAppDetails().getOs()).appId(request.getAppDetails().getAppId())
                                .msisdn(request.getUserDetails().getMsisdn()).buildNo(request.getAppDetails().getBuildNo()).countryCode(request.getUserDetails().getCountryCode())
                                .si(request.getUserDetails().getSi())
                                .build());

        final Optional<Integer> optionalTrialPlanId = Optional.of(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId)))
                .filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL);
        if (optionalTrialPlanId.isPresent()) {
            SelectivePlansComputationResponse trialPlanComputationResponse = subscriptionServiceManager.compute(
                    SelectivePlanEligibilityRequest.builder().userDetails(request.getUserDetails()).appDetails(request.getAppDetails()).planId(optionalTrialPlanId.get()).service(paidPlan.getService())
                            .build());
            trialEligible = trialPlanComputationResponse.getEligiblePlans().contains(optionalTrialPlanId.get());
        }
        final FilteredPaymentOptionsResult.FilteredPaymentOptionsResultBuilder builder = FilteredPaymentOptionsResult.builder().trialEligible(trialEligible).eligibilityRequest(eligibilityRequest);
        if (trialEligible)
            return builder.methods(getFilteredPaymentGroups((PaymentMethod::isTrialSupported), (paidPlan::supportAutoRenew), eligibilityRequest)).build();
        return builder.methods(getFilteredPaymentGroups((paymentMethod -> true), (paidPlan::supportAutoRenew), eligibilityRequest)).build();
    }

    private FilteredPaymentOptionsResult getPaymentOptionsDetailsForPoint(IPaymentOptionsRequest request) {
        final ItemDTO item = paymentCachingService.getItem(request.getProductDetails().getId());
        PaymentOptionsEligibilityRequest eligibilityRequest = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().itemDTO(item)
                .couponCode(request.getCouponId())
                .os(request.getAppDetails().getOs())
                .appId(request.getAppDetails().getAppId())
                .buildNo(request.getAppDetails().getBuildNo())
                .countryCode(request.getUserDetails().getCountryCode())
                .si(request.getUserDetails().getSi())
                .build());
        final List<in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO> filteredMethods = getFilteredPaymentGroups((paymentMethod -> true), (() -> false), eligibilityRequest);
        return FilteredPaymentOptionsResult.builder().eligibilityRequest(eligibilityRequest).methods(filteredMethods).build();
    }

    private List<in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO> getFilteredPaymentGroups(Predicate<PaymentMethod> filterPredicate, Supplier<Boolean> autoRenewalSupplier, PaymentOptionsEligibilityRequest request) {
        final List<in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO> finalMethods = new ArrayList<>();
        final Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            request.setGroup(group.getId());
            List<PaymentMethod> filterMethods = availableMethods.get(group.getId()).stream().filter(filterPredicate).collect(Collectors.toList());
            final PaymentOptionsComputationResponse response = paymentOptionManager.compute(request);
            filterMethods = filterPaymentMethodsBasedOnEligibility(response, filterMethods);
            List<in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO> filteredDTO = filterMethods.stream().map((pm) -> new in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO(pm, autoRenewalSupplier)).collect(Collectors.toList());
            finalMethods.addAll(filteredDTO);
        }
        return finalMethods;
    }

    private List<PaymentMethod> filterPaymentMethodsBasedOnEligibility(PaymentOptionsComputationResponse response, List<PaymentMethod> methods) {
        Set<PaymentMethod> eligibilityResultSet = response.getPaymentMethods();
        return methods.stream().filter(eligibilityResultSet::contains).collect(Collectors.toList());
    }

//    private void addPaymentMethod(PaymentMethod paymentMethod, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, Supplier<Boolean> autoRenewalSupplier) {
//        String group = paymentMethod.getGroup();
//        List<AbstractPaymentOptions> payOptions = new ArrayList<>();
//        //if APS, check if it comes into eligible methods and update other details required for UI as well
//        if (AIRTEL_PAY_STACK.equals(paymentMethod.getPaymentCode().getCode())) {
//            payOptions.forEach(payOption -> {
//
//                if (common.isGroupEligible(payOption.getType(), group)) {
//                    switch (group) {
//                        case UPI:
//                            UpiPaymentOptions upiPaymentOptions = (UpiPaymentOptions) payOption;
//                            upiPaymentOptions.getUpiSupportedApps().forEach(upiSupportedApps -> {
//                                if (upiSupportedApps.getUpiPspAppName().equalsIgnoreCase((String) paymentMethod.getMeta().get(APP_NAME))) {
//                                    addUpiDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, upiSupportedApps.getHealth());
//                                }
//                            });
//                            break;
//                        case CARD:
//                            addCardDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier);
//                            break;
//                        case NET_BANKING:
//                            NetBankingPaymentOptions netBankingPaymentOptions = (NetBankingPaymentOptions) payOption;
//                            netBankingPaymentOptions.getSubOption().forEach(netBankingSubOptions -> {
//                                if (netBankingSubOptions.getSubType().equalsIgnoreCase((String) paymentMethod.getMeta().get("bankCode"))) {
//                                    addNetBankingDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, netBankingSubOptions.getHealth());
//                                }
//                            });
//                            break;
//                        case WALLET:
//                            WalletPaymentsOptions walletPaymentsOptions = (WalletPaymentsOptions) payOption;
//                            //TODO: update code for wallet from APS
//                            walletPaymentsOptions.getWalletSubOption().forEach(walletSubOption -> {
//                                if (walletSubOption.getSubType().equalsIgnoreCase(paymentMethod.getSubtitle())) {
//                                    addNetWalletDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, walletSubOption.getHealth());
//                                }
//                            });
//                            break;
//                    }
//                }
//            });
//        } else {
//            addEligiblePaymentMethodForPayU(group, paymentMethodDTO, paymentMethod, autoRenewalSupplier);
//        }
//
//    }
//
//    private void addEligiblePaymentMethodForPayU(String group, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier) {
//        switch (group) {
//            case UPI:
//                addUpiDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, null);
//                break;
//            case CARD:
//                addCardDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier);
//                break;
//            case NET_BANKING:
//                addNetBankingDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, null);
//                break;
//            case WALLET:
//                addNetWalletDetails(paymentMethodDTO, paymentMethod, autoRenewalSupplier, null);
//                break;
//
//            case ADDTOBILL:
//                if (Objects.isNull(paymentMethodDTO.getAddToBills())) {
//                    paymentMethodDTO.setAddToBills(new ArrayList<>());
//                }
//                final String description =
//                        Objects.nonNull(paymentMethod.getMeta()) && Objects.nonNull(paymentMethod.getMeta().get(META_DESCRIPTION)) ? (String) paymentMethod.getMeta().get(META_DESCRIPTION) : null;
//                paymentMethodDTO.getAddToBills()
//                        .add(AddToBill.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
//                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
//                                .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build()).build());
//                break;
//
//            //TODO: Google Play Billing Phase 2
//            /*case PaymentConstants.BILLING:
//                if (Objects.isNull(paymentMethodDTO.getAddToBills())) {
//                    paymentMethodDTO.setAddToBills(new ArrayList<>());
//                }
//                paymentMethodDTO.getAddToBills()
//                        .add(AddToBill.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
//                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
//                                .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build())
//                                .build());
//                break;
//            default:
//                throw new WynkRuntimeException("Payment Method not supported");*/
//        }
//
//    }
//
//
//    private void addNetWalletDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier,
//                                     String health) {
//
//        if (Objects.isNull(paymentMethodDTO.getWallet())) {
//            paymentMethodDTO.setWallet(new ArrayList<>());
//        }
//        if (Objects.isNull(paymentMethod.getMeta())) {
//            throw new WynkRuntimeException("Meta information is missing in payment method");
//        }
//        paymentMethodDTO.getWallet()
//                .add(Wallet.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).health(health).description((String) paymentMethod.getMeta().get(META_DESCRIPTION))
//                        .code(paymentMethod.getPaymentCode().getCode())
//                        .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
//                        .supportingDetails(CardSupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported())
//                                .intentDetails(CardSupportingDetails.IntentDetails.builder().packageName((String) paymentMethod.getMeta().get(META_PACKAGE_NAME)).build()).build())
//                        .build());
//    }
//
//    private void addNetBankingDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier,
//                                      String health) {
//        if (Objects.isNull(paymentMethodDTO.getNetBanking())) {
//            paymentMethodDTO.setNetBanking(new ArrayList<>());
//        }
//        final String description =
//                Objects.nonNull(paymentMethod.getMeta()) && Objects.nonNull(paymentMethod.getMeta().get(META_DESCRIPTION)) ? (String) paymentMethod.getMeta().get(META_DESCRIPTION) : null;
//        paymentMethodDTO.getNetBanking()
//                .add(NetBanking.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).health(health).description(description).code(paymentMethod.getPaymentCode().getCode())
//                        .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
//                        .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build())
//                        .build());
//    }
//
//    private void addCardDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier) {
//        if (Objects.isNull(paymentMethodDTO.getCard())) {
//            paymentMethodDTO.setCard(new ArrayList<>());
//        }
//        Map<String, String> map = new HashMap<>();
//        map.put("icon", paymentMethod.getIconUrl());
//        map.putAll((Map<String, String>) paymentMethod.getMeta().get("card_type_to_icon_map"));
//        final String description =
//                Objects.nonNull(paymentMethod.getMeta()) && Objects.nonNull(paymentMethod.getMeta().get(META_DESCRIPTION)) ? (String) paymentMethod.getMeta().get(META_DESCRIPTION) : null;
//        boolean saveSupported = Objects.nonNull(paymentMethod.getMeta().get(SAVE_SUPPORTED)) && (boolean) paymentMethod.getMeta().get(SAVE_SUPPORTED);
//        paymentMethodDTO.getCard()
//                .add(Card.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
//                        .uiDetails(map).supportingDetails(CardSupportingDetails.builder().saveSupported(saveSupported).intentDetails(
//                                CardSupportingDetails.IntentDetails.builder().packageName((String) paymentMethod.getMeta().get(META_PACKAGE_NAME)).build()).build()).build());
//    }
//
//    private void addUpiDetails(PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, PaymentMethod paymentMethod, Supplier<Boolean> autoRenewalSupplier,
//                               String health) {
//        if (Objects.isNull(paymentMethod.getMeta())) {
//            throw new WynkRuntimeException("Meta information is missing in payment method");
//        }
//
//        if (Objects.isNull(paymentMethodDTO.getUpi())) {
//            paymentMethodDTO.setUpi(new ArrayList<>());
//        }
//
//        boolean saveSupported = Objects.nonNull(paymentMethod.getMeta().get(SAVE_SUPPORTED)) && (boolean) paymentMethod.getMeta().get(SAVE_SUPPORTED);
//        paymentMethodDTO.getUpi()
//                .add(in.wynk.payment.dto.response.upi.UPI.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).health(health)
//                        .description((String) paymentMethod.getMeta().get(META_DESCRIPTION))
//                        .code(paymentMethod.getPaymentCode().getCode()).appName((String) paymentMethod.getMeta().get(APP_NAME))
//                        .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
//                        .supportingDetails(in.wynk.payment.dto.response.upi.UPI.UpiSupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported())
//                                .suffixes(paymentMethod.getSuffixes()).paymentStatusPoll((Double) paymentMethod.getMeta().get("payment_status_poll")).saveSupported(saveSupported)
//                                .paymentTimer((Double) paymentMethod.getMeta().get("payment_timer")).packageName((String) paymentMethod.getMeta().get(META_PACKAGE_NAME))
//                                .intent((Boolean) paymentMethod.getMeta().get("intent"))
//                                .buildCheck((Map<String, Map<String, Integer>>) paymentMethod.getMeta().get("build_check")).build())
//                        .build());
//    }
//
//
//    private PaymentOptionsDTO.PlanDetails buildPlanDetails(String planId, boolean trialEligible) {
//        PlanDTO plan = paymentCachingService.getPlan(planId);
//        OfferDTO offer = paymentCachingService.getOffer(plan.getLinkedOfferId());
//        PartnerDTO partner = paymentCachingService.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
//        PaymentOptionsDTO.PlanDetails.PlanDetailsBuilder<?, ?> planDetailsBuilder =
//                PaymentOptionsDTO.PlanDetails.builder().id(planId).validityUnit(plan.getPeriod().getValidityUnit()).perMonthValue((int) plan.getPrice().getMonthlyAmount())
//                        .discountedPrice(plan.getPrice().getAmount()).price((int) plan.getPrice().getDisplayAmount()).discount(plan.getPrice().getSavings()).partnerLogo(partner.getPartnerLogo())
//                        .month(plan.getPeriod().getMonth()).freeTrialAvailable(trialEligible).partnerName(partner.getName()).dailyAmount(plan.getPrice().getDailyAmount())
//                        .currency(plan.getPrice().getCurrency()).title(offer.getTitle()).day(plan.getPeriod().getDay()).sku(plan.getSku()).subType(plan.getPlanType().getValue());
//        if (trialEligible) {
//            final PlanDTO trialPlan = paymentCachingService.getPlan(plan.getLinkedFreePlanId());
//            planDetailsBuilder.trialDetails(
//                    PaymentOptionsDTO.TrialPlanDetails.builder().id(String.valueOf(trialPlan.getId())).day(trialPlan.getPeriod().getDay()).month(trialPlan.getPeriod().getMonth())
//                            .validityUnit(trialPlan.getPeriod().getValidityUnit()).validity(trialPlan.getPeriod().getValidity()).currency(trialPlan.getPrice().getCurrency())
//                            .timeUnit(trialPlan.getPeriod().getTimeUnit()).build());
//        }
//        return planDetailsBuilder.build();
//    }
//
//    private List<AbstractSavedPaymentDTO> addSavedPaymentOptions(List<AbstractSavedPayOptions> payOptions, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO) {
//        List<AbstractSavedPaymentDTO> abstractSavedPaymentDTOList = new ArrayList<>();
//        for (AbstractSavedPayOptions payOption : payOptions) {
//            switch (payOption.getType()) {
//                case UPI:
//                    UpiSavedOptions upiDetails = (UpiSavedOptions) payOption;
//                    List<in.wynk.payment.dto.response.upi.UPI> upiPayment =
//                            paymentMethodDTO.getUpi().stream().filter(upi -> upi.getCode().equals(AIRTEL_PAY_STACK) && upi.getAppName().equals(upiDetails.getUpiApp())).collect(Collectors.toList());
//                    if (upiPayment.size() > 0) {
//                        abstractSavedPaymentDTOList.add(UpiSavedDetails.builder().id(upiPayment.get(0).getId()).code(AIRTEL_PAY_STACK).group(upiDetails.getType()).isFavorite(upiDetails.isFavourite())
//                                .isRecommended(upiDetails.isPreferred()).health(upiDetails.getHealth()).vpa(upiDetails.getUserVPA()).build());
//                    }
//
//                    break;
//                case CARDS:
//                    CardSavedPayOptions cardDetails = (CardSavedPayOptions) payOption;
//                    List<Card> cardPayment = paymentMethodDTO.getCard().stream().filter(card -> card.getCode().equals(AIRTEL_PAY_STACK)).collect(Collectors.toList());
//                    if (cardPayment.size() > 0) {
//                        abstractSavedPaymentDTOList.add(
//                                CardSavedDetails.builder().id(cardPayment.get(0).getId()).code(AIRTEL_PAY_STACK).group(cardDetails.getType()).isFavorite(cardDetails.isFavourite())
//                                        .isRecommended(cardDetails.isPreferred()).health(cardDetails.getHealth()).autoPayEnabled(cardDetails.isAutoPayEnable()).cardToken(cardDetails.getCardRefNo())
//                                        .cardNumber(cardDetails.getCardNumber()).cardType(cardDetails.getCardType()).cardCategory(cardDetails.getCardCategory()).cardbin(cardDetails.getCardBin())
//                                        .bankName(cardDetails.getCardBankName()).bankCode(cardDetails.getBankCode()).expiryMonth(cardDetails.getExpiryMonth()).expiryYear(cardDetails.getExpiryYear())
//                                        .cvv(Integer.parseInt(cardDetails.getCvvLength())).icon(cardDetails.getIconUrl()).isFavorite(cardDetails.isFavourite()).isRecommended(cardDetails.isPreferred())
//                                        .active(!cardDetails.isExpired() && !cardDetails.isBlocked()).build());
//                    }
//                    break;
//                //TODo: Phase 2
//                /*case "WALLETS":
//                    WalletSavedOptions walletDetails = (WalletSavedOptions) payOption;
//                    abstractSavedPaymentDTOList.add(
//                            WalletSavedInfo.builder()*//*.id()*//*.code(AIRTEL_PAY_STACK).group(walletDetails.getType()).isFavorite(walletDetails.isFavourite()).isRecommended(walletDetails
//                            .isPreferred())
//                               .health(walletDetails.getHealth()).linked(walletDetails.isLinked()).valid(walletDetails.isValid()).canCheckOut(walletDetails.isShowOnQuickCheckout())
//                                    .addMoneyAllowed(true).id(walletDetails.getWalletId()).balance(walletDetails.getWalletBalance())
//                                    .minBalance(walletDetails.getMinAmount()).build());*/
//
//            }
//        }
//        return abstractSavedPaymentDTOList;
//    }
//
//    private PaymentOptionsDTO.PointDetails buildPointDetails(ItemDTO item) {
//        return PaymentOptionsDTO.PointDetails.builder()
//                .id(item.getId())
//                .title(item.getName())
//                .price(item.getPrice())
//                .build();
//    }
}
