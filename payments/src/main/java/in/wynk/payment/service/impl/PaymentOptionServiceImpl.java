package in.wynk.payment.service.impl;

import in.wynk.common.dto.*;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.dto.WebPaymentOptionsRequest;
import in.wynk.payment.dto.gpbs.GooglePlayConstant;
import in.wynk.payment.dto.request.AbstractPaymentOptionsRequest;
import in.wynk.payment.dto.request.AbstractPreferredPaymentDetailsControllerRequest;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
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
import in.wynk.subscription.common.request.UserPersonalisedPlanRequest;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY022;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY023;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOptionServiceImpl implements IPaymentOptionService, IUserPreferredPaymentService<CombinedPaymentDetailsResponse, AbstractPreferredPaymentDetailsControllerRequest<?>> {

    private static final int N = 3;
    private final IUserPaymentsManager userPaymentsManager;
    private final PaymentCachingService paymentCachingService;
    private final IPaymentOptionComputationManager paymentOptionManager;
    private final SubscriptionServiceManagerImpl subscriptionServiceManager;

    @Override
    @Deprecated
    public WynkResponseEntity<PaymentOptionsDTO> getPaymentOptions(String planId, String itemId) {
        if (!StringUtils.isEmpty(planId) && paymentCachingService.containsPlan(planId)) {
            return getPaymentOptionsForPlan(planId);
        }
        if (!StringUtils.isEmpty(itemId) && paymentCachingService.containsItem(itemId)) {
            return getPaymentOptionsForItem(planId);
        }
        throw new WynkRuntimeException("Unknown planId or ItemId is supplied");
    }

    @Override
    public WynkResponseEntity<PaymentOptionsDTO> getFilteredPaymentOptions(AbstractPaymentOptionsRequest<?> request) {
        try {
            if (request.getPaymentOptionRequest().getProductDetails().getType().equalsIgnoreCase(PLAN)) {
                return getFilteredPaymentOptionsForPlan(request.getPaymentOptionRequest());
            } else if (request.getPaymentOptionRequest().getProductDetails().getType().equalsIgnoreCase(POINT)) {
                return getFilteredPaymentOptionsForItem(request.getPaymentOptionRequest());
            } else {
                log.info(PAYMENT_OPTIONS_FAILURE, "Either planId or itemId is mandatory for paymentOptions");
                throw new WynkRuntimeException(PAY023);
            }
        } catch (Exception ex) {
            log.info(PAYMENT_OPTIONS_FAILURE, "Can't fetch payment options for plan/item {} and msisdn {}", request.getPaymentOptionRequest().getProductDetails().getId(), request.getPaymentOptionRequest().getUserDetails().getMsisdn());
            throw new WynkRuntimeException(PAY022, ex);
        }
    }

    @Deprecated
    private WynkResponseEntity<PaymentOptionsDTO> getPaymentOptionsForPlan(String planId) {
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<PaymentOptionsDTO> responseEntityBuilder = WynkResponseEntity.<PaymentOptionsDTO>builder();
        final PlanDTO paidPlan = paymentCachingService.getPlan(planId);
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder builder = PaymentOptionsDTO.builder();
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final Set<Integer> eligiblePlanIds = sessionDTO.get(ELIGIBLE_PLANS);
        final boolean trialEligible = Optional.ofNullable(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId))).filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL).map(trialPlanId -> !CollectionUtils.isEmpty(eligiblePlanIds) && eligiblePlanIds.contains(trialPlanId)).orElse(false);
        if (trialEligible)
            builder.paymentGroups(getPaymentGroups((PaymentMethod::isTrialSupported), () -> paidPlan.supportAutoRenew(), paidPlan));
        else builder.paymentGroups(getPaymentGroups((paymentMethod -> true), () -> paidPlan.supportAutoRenew(), paidPlan));
        final WebPaymentOptionsRequest request = WebPaymentOptionsRequest.builder().build();
        return responseEntityBuilder.status(httpStatus).data(builder.msisdn(sessionDTO.get(MSISDN)).productDetails(buildPlanDetails(request.getUserDetails(), request.getAppDetails(), request.getGeoLocation(), sessionDTO.get(UID), planId, trialEligible)).build()).build();
    }

    @Deprecated
    private WynkResponseEntity<PaymentOptionsDTO> getPaymentOptionsForItem(String planId) {
        HttpStatus httpStatus = HttpStatus.OK;
        final PlanDTO paidPlan = paymentCachingService.getPlan(planId);
        WynkResponseEntity.WynkResponseEntityBuilder<PaymentOptionsDTO> responseEntityBuilder = WynkResponseEntity.<PaymentOptionsDTO>builder();
        return responseEntityBuilder.status(httpStatus).data(PaymentOptionsDTO.builder().productDetails(buildPointDetails(planId)).paymentGroups(getPaymentGroups((paymentMethod -> true), () -> false,
                paidPlan)).build()).build();
    }

    private List<PaymentOptionsDTO.PaymentGroupsDTO> getPaymentGroups (Predicate<PaymentMethod> filterPredicate, Supplier<Boolean> autoRenewalSupplier,
                                                                       PlanDTO planDTO) {
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<PaymentOptionsDTO.PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            List<PaymentMethodDTO> methodDTOS =
                    availableMethods.get(group.getId()).stream()
                            .filter(paymentMethod -> !paymentMethod.getPaymentCode().getCode().equals("aps"))
                            .filter(filterPredicate).map((pm) -> new PaymentMethodDTO(pm, autoRenewalSupplier)).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(methodDTOS)) {
                PaymentOptionsDTO.PaymentGroupsDTO groupsDTO =
                        PaymentOptionsDTO.PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group.getId()).displayName(group.getDisplayName()).hierarchy(group.getHierarchy()).build();
                if (GooglePlayConstant.BILLING.equalsIgnoreCase(groupsDTO.getPaymentGroup())) {
                    String os = SessionContextHolder.<SessionDTO>getBody().get(OS);
                    if (os.equalsIgnoreCase(ANDROID) && !CollectionUtils.isEmpty(planDTO.getSku())) {
                        paymentGroupsDTOS.add(groupsDTO);
                    }
                } else {
                    paymentGroupsDTOS.add(groupsDTO);
                }
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

    @Deprecated
    private PaymentOptionsDTO.PointDetails buildPointDetails(String itemId) {
        final ItemDTO item = paymentCachingService.getItem(itemId);
        return PaymentOptionsDTO.PointDetails.builder()
                .id(itemId)
                .title(item.getName())
                .price(item.getPrice())
                .build();
    }

    private PaymentOptionsDTO.PlanDetails buildPlanDetails(IUserDetails userDetails, IAppDetails appDetails, IGeoLocation geoDetails, String uid, String planId, boolean trialEligible) {
        PlanDTO plan = subscriptionServiceManager.getUserPersonalisedPlanOrDefault(UserPersonalisedPlanRequest.builder().userDetails(((in.wynk.payment.dto.UserDetails) userDetails).toUserDetails(uid)).appDetails(((AppDetails) appDetails).toAppDetails()).geoDetails((GeoLocation) geoDetails).planId(NumberUtils.toInt(planId)).build(), paymentCachingService.getPlan(planId));
        OfferDTO offer = paymentCachingService.getOffer(plan.getLinkedOfferId());
        PartnerDTO partner = paymentCachingService.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
        PaymentOptionsDTO.PlanDetails.PlanDetailsBuilder<?, ?> planDetailsBuilder = PaymentOptionsDTO.PlanDetails.builder()
                .id(planId)
                .validityUnit(plan.getPeriod().getValidityUnit())
                .perMonthValue((int) plan.getPrice().getMonthlyAmount())
                .discountedPrice(plan.getPrice().getAmount())
                .price((int) plan.getPrice().getDisplayAmount())
                .discount(plan.getPrice().getSavings())
                .partnerLogo(partner.getPartnerLogo())
                .month(plan.getPeriod().getMonth())
                .freeTrialAvailable(trialEligible)
                .partnerName(partner.getName())
                .dailyAmount(plan.getPrice().getDailyAmount())
                .currency(plan.getPrice().getCurrency())
                .title(offer.getTitle())
                .day(plan.getPeriod().getDay())
                .sku(plan.getSku())
                .subType(plan.getPlanType().getValue());
        if (trialEligible) {
            final PlanDTO trialPlan = paymentCachingService.getPlan(plan.getLinkedFreePlanId());
            planDetailsBuilder.trialDetails(PaymentOptionsDTO.TrialPlanDetails.builder().id(String.valueOf(trialPlan.getId())).day(trialPlan.getPeriod().getDay()).month(trialPlan.getPeriod().getMonth()).validityUnit(trialPlan.getPeriod().getValidityUnit()).validity(trialPlan.getPeriod().getValidity()).currency(trialPlan.getPrice().getCurrency()).timeUnit(trialPlan.getPeriod().getTimeUnit()).build());
        }
        return planDetailsBuilder.build();
    }

    @Override
    public WynkResponseEntity<CombinedPaymentDetailsResponse> getUserPreferredPayments(AbstractPreferredPaymentDetailsControllerRequest<?> request) {
        final String uid = request.getUid();
        final String deviceId = request.getDeviceId();
        final String clientAlias = request.getClient();
        final String si = request.getSi();
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
                    IUserPreferredPaymentService<AbstractPaymentDetails, AbstractPreferredPaymentDetailsRequest<?>> userPreferredPaymentService = BeanLocatorFactory.getBean(PaymentCodeCachingService.getFromPaymentCode(paymentCode).getCode(), new ParameterizedTypeReference<IUserPreferredPaymentService<AbstractPaymentDetails, AbstractPreferredPaymentDetailsRequest<?>>>() {
                    });
                    String requestId = MDC.get(REQUEST_ID);
                    task = () -> {
                        MDC.put(REQUEST_ID, requestId);
                        return userPreferredPaymentService.getUserPreferredPayments(PreferredPaymentDetailsRequest.builder().clientAlias(clientAlias).si(si).productDetails(request.getProductDetails()).couponId(request.getCouponId()).preferredPayment(userPreferredPaymentMap.getOrDefault(keyBuilder.build(), UserPreferredPayment.builder().id(keyBuilder.build()).build())).build());
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

    private WynkResponseEntity<PaymentOptionsDTO> getFilteredPaymentOptionsForPlan(IPaymentOptionsRequest request) {
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<PaymentOptionsDTO> responseEntityBuilder = WynkResponseEntity.<PaymentOptionsDTO>builder();
        final PlanDTO paidPlan = paymentCachingService.getPlan(request.getProductDetails().getId());
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder builder = PaymentOptionsDTO.builder();
        PaymentOptionsEligibilityRequest eligibilityRequest = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder()
                .planDTO(paidPlan)
                .client(request.getClient())
                .couponCode(request.getCouponId())
                .os(request.getAppDetails().getOs())
                .appId(request.getAppDetails().getAppId())
                .msisdn(request.getUserDetails().getMsisdn())
                .buildNo(request.getAppDetails().getBuildNo())
                .countryCode(request.getUserDetails().getCountryCode())
                .si(request.getUserDetails().getSi())
                .build());
        boolean trialEligible = false;
        final Optional<Integer> optionalTrialPlanId = Optional.ofNullable(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId))).filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL);
        if (optionalTrialPlanId.isPresent()) {
            SelectivePlansComputationResponse trialPlanComputationResponse = subscriptionServiceManager.compute(SelectivePlanEligibilityRequest.builder().userDetails(request.getUserDetails()).appDetails(request.getAppDetails()).planId(optionalTrialPlanId.get()).service(paidPlan.getService()).build());
            trialEligible = trialPlanComputationResponse.getEligiblePlans().contains(optionalTrialPlanId.get());
        }
        if (trialEligible)
            builder.paymentGroups(getFilteredPaymentGroups((PaymentMethod::isTrialSupported), (paidPlan::supportAutoRenew), eligibilityRequest, paidPlan));
        else if(Objects.nonNull(request.getMiscellaneousDetails()) && request.getMiscellaneousDetails().isAutoRenew())
            builder.paymentGroups(getFilteredPaymentGroups((PaymentMethod::isAutoRenewSupported), (paidPlan::supportAutoRenew), eligibilityRequest, paidPlan));
        else builder.paymentGroups(getFilteredPaymentGroups((paymentMethod -> true), (paidPlan::supportAutoRenew), eligibilityRequest, paidPlan));
        return responseEntityBuilder.status(httpStatus).data(builder.msisdn(request.getUserDetails().getMsisdn()).productDetails(buildPlanDetails(request.getUserDetails(), request.getAppDetails(), request.getGeoLocation(), eligibilityRequest.getUid() ,request.getProductDetails().getId(), trialEligible)).build()).build();
    }

    private WynkResponseEntity<PaymentOptionsDTO> getFilteredPaymentOptionsForItem(IPaymentOptionsRequest request) {
        HttpStatus httpStatus = HttpStatus.OK;
        final WynkResponseEntity.WynkResponseEntityBuilder<PaymentOptionsDTO> responseEntityBuilder = WynkResponseEntity.<PaymentOptionsDTO>builder();
        final ItemDTO item = paymentCachingService.getItem(request.getProductDetails().getId());
        PaymentOptionsEligibilityRequest eligibilityRequest = PaymentOptionsEligibilityRequest.from(PaymentOptionsComputationDTO.builder().itemDTO(item)
                .client(request.getClient())
                .couponCode(request.getCouponId())
                .os(request.getAppDetails().getOs())
                .appId(request.getAppDetails().getAppId())
                .buildNo(request.getAppDetails().getBuildNo())
                .countryCode(request.getUserDetails().getCountryCode())
                .si(request.getUserDetails().getSi())
                .build());
        return responseEntityBuilder.status(httpStatus).data(PaymentOptionsDTO.builder().productDetails(buildPointDetails(item)).paymentGroups(getFilteredPaymentGroups((paymentMethod -> true), (() -> false), eligibilityRequest, null)).build()).build();
    }

    private List<PaymentOptionsDTO.PaymentGroupsDTO> getFilteredPaymentGroups (Predicate<PaymentMethod> filterPredicate, Supplier<Boolean> autoRenewalSupplier,
                                                                               PaymentOptionsEligibilityRequest request, PlanDTO planDTO) {
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<PaymentOptionsDTO.PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            request.setGroup(group.getId());
            List<PaymentMethod> methods = availableMethods.get(group.getId()).stream().filter(filterPredicate).filter(paymentMethod -> !paymentMethod.getPaymentCode().getCode().equals("aps")).collect(Collectors.toList());
            final PaymentOptionsComputationResponse response = paymentOptionManager.compute(request);
            methods = filterPaymentMethodsBasedOnEligibility(response, methods);
            List<PaymentMethodDTO> methodDTOS = methods.stream().map((pm) -> new PaymentMethodDTO(pm, autoRenewalSupplier)).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(methodDTOS)) {
                PaymentOptionsDTO.PaymentGroupsDTO groupsDTO =
                        PaymentOptionsDTO.PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group.getId()).displayName(group.getDisplayName()).hierarchy(group.getHierarchy()).build();
                if (GooglePlayConstant.BILLING.equalsIgnoreCase(groupsDTO.getPaymentGroup())) {
                    if (ANDROID.equalsIgnoreCase(request.getOs()) && !CollectionUtils.isEmpty(planDTO.getSku())) {
                        paymentGroupsDTOS.add(groupsDTO);
                    }
                } else {
                    paymentGroupsDTOS.add(groupsDTO);
                }
            }
        }
        return paymentGroupsDTOS;
    }

}