package in.wynk.payment.service.impl;

import in.wynk.common.constant.BaseConstants;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.UpiConstants;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.billing.Billing;
import in.wynk.payment.dto.response.card.Card;
import in.wynk.payment.dto.response.netbanking.NetBanking;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;
import in.wynk.payment.dto.response.upi.UPI;
import in.wynk.payment.dto.response.wallet.Wallet;
import in.wynk.payment.eligibility.request.PaymentOptionsComputationDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.service.IPaymentOptionComputationManager;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
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
    private final IPaymentOptionComputationManager paymentOptionManager;
    private final SubscriptionServiceManagerImpl subscriptionServiceManager;

    /**
     * Point sale is for selling individual items like movie or song
     *
     * @param request
     * @return
     */
    @Override
    public PaymentOptionsDTO getPaymentOptions (AbstractPaymentOptionsRequest<?> request) {
        PaymentOptionsDTO paymentOptions;
        if (request.getPaymentOptionRequest().getProductDetails().getType().equalsIgnoreCase(PLAN)) {
            paymentOptions = getPaymentOptionsDetailsForPlan(request.getPaymentOptionRequest());

        } else if (request.getPaymentOptionRequest().getProductDetails().getType().equalsIgnoreCase(POINT)) {
            paymentOptions = getPaymentOptionsDetailsForPoint(request.getPaymentOptionRequest());
        } else {
            log.info(PAYMENT_OPTIONS_FAILURE, "Either planId or itemId is mandatory for paymentOptions");
            throw new WynkRuntimeException(PAY023);
        }
        return paymentOptions;
    }

    private PaymentOptionsDTO getPaymentOptionsDetailsForPlan (IPaymentOptionsRequest request) {
        boolean trialEligible = false;
        PaymentOptionsDTO.PaymentOptionsDTOBuilder builder = PaymentOptionsDTO.builder();
        final PlanDTO paidPlan = paymentCachingService.getPlan(request.getProductDetails().getId());
        PaymentOptionsEligibilityRequest eligibilityRequest =
                PaymentOptionsEligibilityRequest.from(
                        PaymentOptionsComputationDTO.builder().planDTO(paidPlan).couponCode(request.getCouponId()).os(request.getAppDetails().getOs()).appId(request.getAppDetails().getAppId())
                                .msisdn(request.getUserDetails().getMsisdn()).buildNo(request.getAppDetails().getBuildNo()).countryCode(request.getUserDetails().getCountryCode())
                                .si(request.getUserDetails().getSi())
                                .build());

        final Optional<Integer> optionalTrialPlanId = Optional.ofNullable(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId)))
                .filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL);
        if (optionalTrialPlanId.isPresent()) {
            SelectivePlansComputationResponse trialPlanComputationResponse = subscriptionServiceManager.compute(
                    SelectivePlanEligibilityRequest.builder().userDetails(request.getUserDetails()).appDetails(request.getAppDetails()).planId(optionalTrialPlanId.get()).service(paidPlan.getService())
                            .build());
            trialEligible = trialPlanComputationResponse.getEligiblePlans().contains(optionalTrialPlanId.get());
        }
        PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO = new PaymentOptionsDTO.PaymentMethodDTO();
        List<PaymentGroupsDTO> paymentGroups;
        if (trialEligible) {
            paymentGroups = getFilteredPaymentGroups((PaymentMethod::isTrialSupported), (paidPlan::supportAutoRenew), eligibilityRequest, paidPlan, paymentMethodDTO);
        } else {
            paymentGroups = getFilteredPaymentGroups((paymentMethod -> true), (paidPlan::supportAutoRenew), eligibilityRequest, paidPlan, paymentMethodDTO);
        }

        return builder.paymentGroups(paymentGroups).paymentMethods(paymentMethodDTO).msisdn(request.getUserDetails().getMsisdn())
                .productDetails(buildPlanDetails(request.getProductDetails().getId(), trialEligible)).build();
    }

    private PaymentOptionsDTO getPaymentOptionsDetailsForPoint (IPaymentOptionsRequest request) {
        final ItemDTO item = paymentCachingService.getItem(request.getProductDetails().getId());
        PaymentOptionsEligibilityRequest eligibilityRequest = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().itemDTO(item)
                .couponCode(request.getCouponId())
                .os(request.getAppDetails().getOs())
                .appId(request.getAppDetails().getAppId())
                .buildNo(request.getAppDetails().getBuildNo())
                .countryCode(request.getUserDetails().getCountryCode())
                .si(request.getUserDetails().getSi())
                .build());
        PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO = new PaymentOptionsDTO.PaymentMethodDTO();
        List<PaymentGroupsDTO> paymentGroups = getFilteredPaymentGroups((paymentMethod -> true), (() -> false), eligibilityRequest, null, paymentMethodDTO);
        return PaymentOptionsDTO.builder().paymentGroups(paymentGroups).paymentMethods(paymentMethodDTO).productDetails(buildPointDetails(item)).build();
    }

    private List<PaymentGroupsDTO> getFilteredPaymentGroups (Predicate<PaymentMethod> filterPredicate, Supplier<Boolean> autoRenewalSupplier,
                                                             PaymentOptionsEligibilityRequest request, PlanDTO planDTO,
                                                             PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO) {
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<PaymentGroupsDTO> paymentGroups = new ArrayList<>();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            request.setGroup(group.getId());
            List<PaymentMethod> methods = availableMethods.get(group.getId()).stream().filter(filterPredicate).collect(Collectors.toList());
            final PaymentOptionsComputationResponse response = paymentOptionManager.compute(request);
            methods = filterPaymentMethodsBasedOnEligibility(response, methods);
            Map<String, List<?>> map = new HashMap<>();
            methods.forEach(method -> {
                addPaymentMethod(method, paymentMethodDTO, autoRenewalSupplier);
            });
            if (methods.size() > 0) {
                PaymentGroupsDTO groupsDTO = PaymentGroupsDTO.builder().id(group.getId()).title(group.getDisplayName()).description(group.getDescription()).build();
                if (Objects.nonNull(paymentMethodDTO.getBilling())) {
                    if (BaseConstants.ANDROID.equalsIgnoreCase(request.getOs()) && !CollectionUtils.isEmpty(planDTO.getSku())) {
                        paymentGroups.add(groupsDTO);
                    }
                } else {
                    paymentGroups.add(groupsDTO);
                }
            }
        }
        return paymentGroups;
    }

    private void addPaymentMethod (PaymentMethod paymentMethod, PaymentOptionsDTO.PaymentMethodDTO paymentMethodDTO, Supplier<Boolean> autoRenewalSupplier) {

        String group = paymentMethod.getGroup();
        String description = Objects.nonNull(paymentMethod.getMeta().get("description")) ? (String) paymentMethod.getMeta().get("description") : null;
        List<Object> alertDetails = new ArrayList<>();
        alertDetails.add(Objects.nonNull(paymentMethod.getMeta().get("alert")) ? (String) paymentMethod.getMeta().get("alert") : null);
        switch (group) {
            case UpiConstants.UPI:
                if (Objects.isNull(paymentMethodDTO.getUpi())) {
                    paymentMethodDTO.setUpi(new ArrayList<>());
                }
                List<UPI.UpiSavedDetails> saved = new ArrayList<>();
                saved.add(UPI.UpiSavedDetails.builder().vpa(Objects.nonNull(paymentMethod.getMeta().get("VPA")) ? (String) paymentMethod.getMeta().get("VPA") : null).build());
                paymentMethodDTO.getUpi()
                        .add(UPI.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .savedDetails(saved)
                                .alertDetails(alertDetails)
                                .supportingDetails(UpiWalletSupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported())
                                        .intentDetails(UpiWalletSupportingDetails.IntentDetails.builder().packageName((String) paymentMethod.getMeta().get("package_name")).build()).build())
                                .build());
                break;
            case PaymentConstants.CARD:
                if (Objects.isNull(paymentMethodDTO.getCard())) {
                    paymentMethodDTO.setCard(new ArrayList<>());
                }
                List<Card.CardSavedDetails> cardSavedDetails = new ArrayList<>();
                cardSavedDetails.add(Card.CardSavedDetails.builder().card(null).build());
                paymentMethodDTO.getCard()
                        .add(Card.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .savedDetails(cardSavedDetails)
                                .build());
                break;
            case PaymentConstants.NET_BANKING:
                if (Objects.isNull(paymentMethodDTO.getNetBanking())) {
                    paymentMethodDTO.setNetBanking(new ArrayList<>());
                }
                paymentMethodDTO.getNetBanking()
                        .add(NetBanking.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .alertDetails(alertDetails)
                                .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build())
                                .build());
                break;
            case PaymentConstants.WALLET:
                if (Objects.isNull(paymentMethodDTO.getWallet())) {
                    paymentMethodDTO.setWallet(new ArrayList<>());
                }
                List<Wallet.WalletSavedDetails> walletSavedDetails = new ArrayList<>();
                walletSavedDetails.add(Wallet.WalletSavedDetails.builder().linkedMobile("123").balance(44.0).addMoneyRequired(true).canCheckout(false).build());
                paymentMethodDTO.getWallet()
                        .add(Wallet.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .savedDetails(walletSavedDetails)
                                .alertDetails(alertDetails)
                                .supportingDetails(UpiWalletSupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported())
                                        .intentDetails(UpiWalletSupportingDetails.IntentDetails.builder().packageName((String) paymentMethod.getMeta().get("package_name")).build()).build())
                                .build());
                break;
            case PaymentConstants.BILLING:
                if (Objects.isNull(paymentMethodDTO.getBilling())) {
                    paymentMethodDTO.setBilling(new ArrayList<>());
                }
                paymentMethodDTO.getBilling()
                        .add(Billing.builder().id(paymentMethod.getId()).title(paymentMethod.getDisplayName()).description(description).code(paymentMethod.getPaymentCode().getCode())
                                .uiDetails(UiDetails.builder().icon(paymentMethod.getIconUrl()).build())
                                .supportingDetails(SupportingDetails.builder().autoRenewSupported(autoRenewalSupplier.get() && paymentMethod.isAutoRenewSupported()).build())
                                .build());
                // .savedDetails( /*paymentMethodDTO.setBilling(Billing.BillingSavedDetails.builder().si("si").type("POSTPAID").build())*/)

                break;
            default:
                throw new WynkRuntimeException("Payment Method not supported");
        }
    }

    private List<PaymentMethod> filterPaymentMethodsBasedOnEligibility (PaymentOptionsComputationResponse response, List<PaymentMethod> methods) {
        Set<PaymentMethod> eligibilityResultSet = response.getPaymentMethods();
        return methods.stream().filter(eligibilityResultSet::contains).collect(Collectors.toList());
    }

    private PaymentOptionsDTO.PlanDetails buildPlanDetails (String planId, boolean trialEligible) {
        PlanDTO plan = paymentCachingService.getPlan(planId);
        OfferDTO offer = paymentCachingService.getOffer(plan.getLinkedOfferId());
        PartnerDTO partner = paymentCachingService.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
        PaymentOptionsDTO.PlanDetails.PlanDetailsBuilder<?, ?> planDetailsBuilder =
                PaymentOptionsDTO.PlanDetails.builder().id(planId).validityUnit(plan.getPeriod().getValidityUnit()).perMonthValue((int) plan.getPrice().getMonthlyAmount())
                        .discountedPrice(plan.getPrice().getAmount()).price((int) plan.getPrice().getDisplayAmount()).discount(plan.getPrice().getSavings()).partnerLogo(partner.getPartnerLogo())
                        .month(plan.getPeriod().getMonth()).freeTrialAvailable(trialEligible).partnerName(partner.getName()).dailyAmount(plan.getPrice().getDailyAmount())
                        .currency(plan.getPrice().getCurrency()).title(offer.getTitle()).day(plan.getPeriod().getDay()).sku(plan.getSku()).subType(plan.getPlanType().getValue());
        if (trialEligible) {
            final PlanDTO trialPlan = paymentCachingService.getPlan(plan.getLinkedFreePlanId());
            planDetailsBuilder.trialDetails(
                    PaymentOptionsDTO.TrialPlanDetails.builder().id(String.valueOf(trialPlan.getId())).day(trialPlan.getPeriod().getDay()).month(trialPlan.getPeriod().getMonth())
                            .validityUnit(trialPlan.getPeriod().getValidityUnit()).validity(trialPlan.getPeriod().getValidity()).currency(trialPlan.getPrice().getCurrency())
                            .timeUnit(trialPlan.getPeriod().getTimeUnit()).build());
        }
        return planDetailsBuilder.build();
    }

    private PaymentOptionsDTO.PointDetails buildPointDetails (ItemDTO item) {
        return PaymentOptionsDTO.PointDetails.builder()
                .id(item.getId())
                .title(item.getName())
                .price(item.getPrice())
                .build();
    }
}
