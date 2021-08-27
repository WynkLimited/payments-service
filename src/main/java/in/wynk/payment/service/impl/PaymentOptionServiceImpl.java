package in.wynk.payment.service.impl;

import in.wynk.common.dto.*;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.SavedDetailsKey;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.dto.request.CombinedPaymentDetailsRequest;
import in.wynk.payment.dto.request.PaymentOptionsRequest;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsComputationResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsComputationDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.service.IPaymentOptionComputationManager;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPaymentsManager;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY022;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOptionServiceImpl implements IPaymentOptionService, IUserPreferredPaymentService<CombinedPaymentDetailsResponse, CombinedPaymentDetailsRequest<?>> {

    private static final int N = 3;
    private final IUserPaymentsManager userPaymentsManager;
    private final PaymentCachingService paymentCachingService;
    private final IPaymentOptionComputationManager paymentOptionManager;

    @Override
    public PaymentOptionsDTO getPaymentOptions(String planId, String itemId) {
        if (!StringUtils.isEmpty(planId) && paymentCachingService.containsPlan(planId)) {
            return getPaymentOptionsForPlan(planId);
        }

        if (!StringUtils.isEmpty(itemId) && paymentCachingService.containsItem(itemId)) {
            return getPaymentOptionsForItem(planId);
        }
        throw new WynkRuntimeException("Unknown planId or ItemId is supplied");
    }

    @Override
    public PaymentOptionsDTO getFilteredPaymentOptions(PaymentOptionsRequest request) {
        try {
            return getFilteredPaymentOptionsForPlan(request);
        } catch (Exception ex) {
            log.info(PAYMENT_OPTIONS_FAILURE,"Can't fetch payment options for plan {} and msisdn {}",request.getPlanId(),request.getUserDetails().getMsisdn());
            throw new WynkRuntimeException(PAY022,ex);
        }
    }

    private PaymentOptionsDTO getPaymentOptionsForPlan(String planId) {
        final PlanDTO paidPlan = paymentCachingService.getPlan(planId);
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder builder = PaymentOptionsDTO.builder();
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final Set<Integer> eligiblePlanIds = sessionDTO.get(ELIGIBLE_PLANS);
        PaymentOptionsEligibilityRequest request = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().planDTO(paidPlan).msisdn(sessionDTO.get(MSISDN)).build());
        final boolean trialEligible = Optional.ofNullable(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId))).filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL).map(trialPlanId -> !CollectionUtils.isEmpty(eligiblePlanIds) && eligiblePlanIds.contains(trialPlanId)).orElse(false);
        if (trialEligible)
            builder.paymentGroups(getPaymentGroups((PaymentMethod::isTrialSupported), request));
        else builder.paymentGroups(getPaymentGroups((paymentMethod -> true), request));
        return builder.msisdn(sessionDTO.get(MSISDN)).productDetails(buildPlanDetails(planId, trialEligible)).build();
    }

    private PaymentOptionsDTO getPaymentOptionsForItem(String itemId) {
        final ItemDTO item = paymentCachingService.getItem(itemId);
        PaymentOptionsEligibilityRequest request = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().itemDTO(item).build());
        return PaymentOptionsDTO.builder().productDetails(buildPointDetails(item)).paymentGroups(getPaymentGroups((paymentMethod -> true), request)).build();
    }

    private List<PaymentOptionsDTO.PaymentGroupsDTO> getPaymentGroups(Predicate<PaymentMethod> filterPredicate, PaymentOptionsEligibilityRequest request) {
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<PaymentOptionsDTO.PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            request.setGroup(group.getId());
            List<PaymentMethod> methods = availableMethods.get(group.getId()).stream().filter(filterPredicate).collect(Collectors.toList());
            final PaymentOptionsComputationResponse response = paymentOptionManager.compute(request);
            methods = filterPaymentMethodsBasedOnEligibility(response, methods);
            List<PaymentMethodDTO> methodDTOS = methods.stream().map(PaymentMethodDTO::new).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(methodDTOS)) {
                PaymentOptionsDTO.PaymentGroupsDTO groupsDTO = PaymentOptionsDTO.PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group.getId()).displayName(group.getDisplayName()).hierarchy(group.getHierarchy()).build();
                paymentGroupsDTOS.add(groupsDTO);
            }
        }
        return paymentGroupsDTOS;
    }

    private List<PaymentMethod> filterPaymentMethodsBasedOnEligibility(PaymentOptionsComputationResponse response, List<PaymentMethod> methods) {
        Set<PaymentMethod> eligibilityResultSet = response.getPaymentMethods();
        return methods.stream().filter(method -> eligibilityResultSet.contains(method)).collect(Collectors.toList());
    }

    private PaymentOptionsDTO.PointDetails buildPointDetails(ItemDTO item) {
        return PaymentOptionsDTO.PointDetails.builder()
                .id(item.getId())
                .title(item.getName())
                .price(item.getPrice())
                .build();
    }

    private PaymentOptionsDTO.PlanDetails buildPlanDetails(String planId, boolean trialEligible) {
        PlanDTO plan = paymentCachingService.getPlan(planId);
        OfferDTO offer = paymentCachingService.getOffer(plan.getLinkedOfferId());
        PartnerDTO partner = paymentCachingService.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
        PaymentOptionsDTO.PlanDetails.PlanDetailsBuilder<?, ?> planDetailsBuilder = PaymentOptionsDTO.PlanDetails.builder()
                .id(planId)
                .validityUnit(plan.getPeriod().getValidityUnit())
                .perMonthValue(plan.getPrice().getMonthlyAmount())
                .discountedPrice(plan.getPrice().getAmount())
                .price(plan.getPrice().getDisplayAmount())
                .discount(plan.getPrice().getSavings())
                .partnerLogo(partner.getPartnerLogo())
                .month(plan.getPeriod().getMonth())
                .freeTrialAvailable(trialEligible)
                .partnerName(partner.getName())
                .dailyAmount(plan.getPrice().getDailyAmount())
                .day(plan.getPeriod().getDay());
        if (trialEligible) {
            final PlanDTO trialPlan = paymentCachingService.getPlan(plan.getLinkedFreePlanId());
            planDetailsBuilder.trialDetails(PaymentOptionsDTO.TrialPlanDetails.builder().id(String.valueOf(trialPlan.getId())).day(trialPlan.getPeriod().getDay()).month(trialPlan.getPeriod().getMonth()).validityUnit(trialPlan.getPeriod().getValidityUnit()).validity(trialPlan.getPeriod().getValidity()).currency(trialPlan.getPrice().getCurrency()).timeUnit(trialPlan.getPeriod().getTimeUnit()).build());
        }
        return planDetailsBuilder.build();
    }

    @Override
    public WynkResponseEntity<CombinedPaymentDetailsResponse> getUserPreferredPayments(CombinedPaymentDetailsRequest<?> request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(UID);
        final String deviceId = sessionDTO.get(DEVICE_ID);
        final ExecutorService executorService = Executors.newFixedThreadPool(N);
        final Map<SavedDetailsKey, Future<WynkResponseEntity<AbstractPaymentDetails>>> map = new HashMap<>();
        final Map<SavedDetailsKey, UserPreferredPayment> userPreferredPaymentMap = userPaymentsManager.get(uid).stream().collect(Collectors.toMap(UserPreferredPayment::getId, Function.identity()));
        Callable<WynkResponseEntity<AbstractPaymentDetails>> task;
        for (String paymentGroup : request.getPaymentGroups().keySet()) {
            for (String paymentCode : request.getPaymentGroups().get(paymentGroup)) {
                SavedDetailsKey.SavedDetailsKeyBuilder keyBuilder = SavedDetailsKey.builder().uid(uid).paymentCode(paymentCode).paymentGroup(paymentGroup);
                if (!userPreferredPaymentMap.containsKey(keyBuilder.build())) {
                    keyBuilder.deviceId(deviceId);
                }
                try {
                    IUserPreferredPaymentService<AbstractPaymentDetails, AbstractPreferredPaymentDetailsRequest<?>> userPreferredPaymentService = BeanLocatorFactory.getBean(PaymentCode.getFromPaymentCode(paymentCode).getCode(), new ParameterizedTypeReference<IUserPreferredPaymentService<AbstractPaymentDetails, AbstractPreferredPaymentDetailsRequest<?>>>() {
                    });
                    String requestId = MDC.get(REQUEST_ID);
                    task = () -> {
                        MDC.put(REQUEST_ID, requestId);
                        return userPreferredPaymentService.getUserPreferredPayments(PreferredPaymentDetailsRequest.builder().productDetails(request.getProductDetails()).couponId(request.getCouponId()).preferredPayment(userPreferredPaymentMap.getOrDefault(keyBuilder.build(), UserPreferredPayment.builder().id(keyBuilder.build()).build())).build());
                    };
                    map.put(keyBuilder.build(), executorService.submit(task));
                } catch (Exception e) {
                    map.put(keyBuilder.build(), null);
                }
            }
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(N, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } finally {
            WynkResponseEntity.WynkResponseEntityBuilder<CombinedPaymentDetailsResponse> builder = WynkResponseEntity.builder();
            Map<String, AbstractPaymentDetails> tempData;
            Map<String, Map<String, AbstractPaymentDetails>> paymentDetailsMap = new HashMap<>();

            Map<String, AbstractErrorDetails> tempError;
            Map<String, Map<String, AbstractErrorDetails>> paymentErrorMap = new HashMap<>();

            for (SavedDetailsKey savedDetailsKey : map.keySet()) {
                try {
                    WynkResponseEntity<AbstractPaymentDetails> details = map.get(savedDetailsKey).get();

                    if (Objects.nonNull(details.getBody().getData())) {
                        tempData = paymentDetailsMap.getOrDefault(savedDetailsKey.getPaymentGroup(), new HashMap<>());
                        tempData.put(savedDetailsKey.getPaymentCode(), details.getBody().getData());
                        paymentDetailsMap.put(savedDetailsKey.getPaymentGroup(), tempData);
                    }

                    if (Objects.nonNull(details.getBody().getError())) {
                        tempError = paymentErrorMap.getOrDefault(savedDetailsKey.getPaymentGroup(), new HashMap<>());
                        tempError.put(savedDetailsKey.getPaymentCode(), (AbstractErrorDetails) details.getBody().getError());
                        paymentErrorMap.put(savedDetailsKey.getPaymentGroup(), tempError);
                    }
                } catch (Exception e) {
                    tempError = paymentErrorMap.getOrDefault(savedDetailsKey.getPaymentGroup(), new HashMap<>());
                    PaymentErrorType paymentErrorType = PaymentErrorType.PAY201;
                    tempError.put(savedDetailsKey.getPaymentCode(), TechnicalErrorDetails.builder().code(paymentErrorType.getErrorCode()).description(paymentErrorType.getErrorMessage()).build());
                    paymentErrorMap.put(savedDetailsKey.getPaymentGroup(), tempError);
                }
            }
            if (!paymentDetailsMap.isEmpty()) {
                builder.data(CombinedPaymentDetailsResponse.builder().details(paymentDetailsMap).build());
            }

            if (!paymentErrorMap.isEmpty()) {
                builder.error(CombinedStandardBusinessErrorDetails.builder().errors(paymentErrorMap).build()).success(false);
            }

            return builder.build();
        }
    }

    private PaymentOptionsDTO getFilteredPaymentOptionsForPlan(PaymentOptionsRequest request) {
        final String planId = request.getPlanId();
        final PlanDTO paidPlan = paymentCachingService.getPlan(planId);
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder builder = PaymentOptionsDTO.builder();
        PaymentOptionsEligibilityRequest eligibilityRequest = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().planDTO(paidPlan).msisdn(request.getUserDetails().getMsisdn()).build());
        final boolean trialEligible = Optional.ofNullable(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId))).filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL).isPresent();
        if (trialEligible)
            builder.paymentGroups(getPaymentGroups((PaymentMethod::isTrialSupported), eligibilityRequest));
        else builder.paymentGroups(getPaymentGroups((paymentMethod -> true), eligibilityRequest));
        return builder.msisdn(request.getUserDetails().getMsisdn()).productDetails(buildPlanDetails(planId, trialEligible)).build();
    }

}