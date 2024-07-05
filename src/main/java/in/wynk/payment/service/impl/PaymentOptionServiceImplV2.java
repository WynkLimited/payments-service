package in.wynk.payment.service.impl;

import in.wynk.common.dto.SessionDTO;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.FraudAware;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.PointDetails;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
import in.wynk.payment.dto.response.PaymentOptionsComputationResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsComputationDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.service.IPaymentOptionComputationManager;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.common.constant.BaseConstants.POINT;
import static in.wynk.payment.core.constant.BeanConstant.OPTION_FRAUD_DETECTION_CHAIN;
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
    @FraudAware(name = OPTION_FRAUD_DETECTION_CHAIN)
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
                        PaymentOptionsComputationDTO.builder().client(request.getClient()).planDTO(paidPlan).couponCode(request.getCouponId()).os(request.getAppDetails().getOs()).appId(request.getAppDetails().getAppId())
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
        if(Objects.nonNull(request.getPaymentDetails()) && request.getPaymentDetails().isMandate())
            return builder.methods(getFilteredPaymentGroups((PaymentMethod::isMandateSupported), (paidPlan::supportAutoRenew), eligibilityRequest)).build();
        if((Objects.nonNull(request.getMiscellaneousDetails()) && request.getMiscellaneousDetails().isAutoRenew()) || (Objects.nonNull(request.getPaymentDetails()) && request.getPaymentDetails().isAutoRenew()))
            return builder.methods(getFilteredPaymentGroups((PaymentMethod::isAutoRenewSupported), (paidPlan::supportAutoRenew), eligibilityRequest)).build();
        return builder.methods(getFilteredPaymentGroups((paymentMethod -> true), (paidPlan::supportAutoRenew), eligibilityRequest)).build();
    }

    private FilteredPaymentOptionsResult getPaymentOptionsDetailsForPoint(IPaymentOptionsRequest request) {
        ItemDTO item = paymentCachingService.getItem(request.getProductDetails().getId());
        if (Objects.isNull(item)) {
            PointDetails pointDetails = (PointDetails) request.getProductDetails();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            if (Objects.nonNull(sessionDTO.get("itemId")) && sessionDTO.get("itemId").equals(pointDetails.getItemId())) {
                ItemDTO.ItemDTOBuilder builder = ItemDTO.builder().id(pointDetails.getItemId());
                if (Objects.nonNull(sessionDTO.get("title"))) {
                    builder.name(sessionDTO.get("title"));
                }
                if (Objects.nonNull(sessionDTO.get("price"))) {
                    builder.price(Double.parseDouble(sessionDTO.get("price")));
                }
                item = builder.build();
            }
        }
        PaymentOptionsEligibilityRequest eligibilityRequest = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().itemDTO(item)
                .client(request.getClient())
                .couponCode(request.getCouponId())
                .os(request.getAppDetails().getOs())
                .appId(request.getAppDetails().getAppId())
                .buildNo(request.getAppDetails().getBuildNo())
                .countryCode(request.getUserDetails().getCountryCode())
                .si(request.getUserDetails().getSi())
                .msisdn(request.getUserDetails().getMsisdn())
                .build());
        final List<in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO> filteredMethods = getFilteredPaymentGroups((PaymentMethod::isItemPurchaseSupported), (() -> false), eligibilityRequest);
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
        return new ArrayList<>(finalMethods.stream().collect(Collectors.toMap(PaymentOptionsDTO.PaymentMethodDTO::getTag, Function.identity(), (pm1, pm2) -> pm1.getHierarchy() < pm2.getHierarchy() ? pm1 : pm2)).values());
    }

    private List<PaymentMethod> filterPaymentMethodsBasedOnEligibility(PaymentOptionsComputationResponse response, List<PaymentMethod> methods) {
        Set<PaymentMethod> eligibilityResultSet = response.getPaymentMethods();
        return methods.stream().filter(eligibilityResultSet::contains).collect(Collectors.toList());
    }

}
