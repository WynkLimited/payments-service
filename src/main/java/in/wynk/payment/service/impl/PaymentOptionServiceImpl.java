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
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.CombinedPaymentDetailsResponse;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
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

@Service
@RequiredArgsConstructor
public class PaymentOptionServiceImpl implements IPaymentOptionService, IUserPreferredPaymentService<CombinedPaymentDetailsResponse, CombinedPaymentDetailsRequest<?>> {

    private static final int N = 3;
    private final IUserPaymentsManager userPaymentsManager;
    private final PaymentCachingService paymentCachingService;

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

    private PaymentOptionsDTO getPaymentOptionsForPlan(String planId) {
        final PlanDTO paidPlan = paymentCachingService.getPlan(planId);
        final PaymentOptionsDTO.PaymentOptionsDTOBuilder builder = PaymentOptionsDTO.builder();
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final Set<Integer> eligiblePlanIds = sessionDTO.get(ELIGIBLE_PLANS);
        final boolean trialEligible = Optional.ofNullable(paidPlan.getLinkedFreePlanId()).filter(trialPlanId -> paymentCachingService.containsPlan(String.valueOf(trialPlanId))).filter(trialPlanId -> paymentCachingService.getPlan(trialPlanId).getPlanType() == PlanType.FREE_TRIAL).map(trialPlanId -> !CollectionUtils.isEmpty(eligiblePlanIds) && eligiblePlanIds.contains(trialPlanId)).orElse(false);
        if (trialEligible)
            builder.paymentGroups(getPaymentGroups((PaymentMethod::isTrialSupported)));
        else builder.paymentGroups(getPaymentGroups((paymentMethod -> true)));
        return builder.msisdn(sessionDTO.get(MSISDN)).productDetails(buildPlanDetails(planId, trialEligible)).build();
    }

    private PaymentOptionsDTO getPaymentOptionsForItem(String itemId) {
        return PaymentOptionsDTO.builder().productDetails(buildPointDetails(itemId)).paymentGroups(getPaymentGroups((paymentMethod -> true))).build();
    }

    private List<PaymentOptionsDTO.PaymentGroupsDTO> getPaymentGroups(Predicate<PaymentMethod> filterPredicate) {
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<PaymentOptionsDTO.PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            List<PaymentMethodDTO> methodDTOS = availableMethods.get(group.getId()).stream().filter(filterPredicate).map(PaymentMethodDTO::new).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(methodDTOS)) {
                PaymentOptionsDTO.PaymentGroupsDTO groupsDTO = PaymentOptionsDTO.PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group.getId()).displayName(group.getDisplayName()).hierarchy(group.getHierarchy()).build();
                paymentGroupsDTOS.add(groupsDTO);
            }
        }
        return paymentGroupsDTOS;
    }

    private PaymentOptionsDTO.PointDetails buildPointDetails(String itemId) {
        final ItemDTO item = paymentCachingService.getItem(itemId);
        return PaymentOptionsDTO.PointDetails.builder()
                .id(itemId)
                .title(item.getName())
                .price(item.getPrice())
                .build();
    }

    private PaymentOptionsDTO.PlanDetails buildPlanDetails(String planId, boolean trialEligible) {
        PlanDTO plan = paymentCachingService.getPlan(planId);
        OfferDTO offer = paymentCachingService.getOffer(plan.getLinkedOfferId());
        PartnerDTO partner = paymentCachingService.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
        return PaymentOptionsDTO.PlanDetails.builder()
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
                .build();
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

}