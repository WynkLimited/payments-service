package in.wynk.payment.service.impl;

import in.wynk.common.dto.*;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.SavedDetailsKey;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.dto.UserPreferredPaymentWrapper;
import in.wynk.payment.dto.request.CombinedPaymentDetailsRequest;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.PaymentDetailsWrapper;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPaymentsManager;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;

@Service
public class PaymentOptionServiceImpl implements IPaymentOptionService {

    private static final int N = 3;
    private final IUserPaymentsManager userPaymentsManager;
    private final PaymentCachingService paymentCachingService;

    public PaymentOptionServiceImpl(IUserPaymentsManager userPaymentsManager, PaymentCachingService paymentCachingService) {
        this.userPaymentsManager = userPaymentsManager;
        this.paymentCachingService = paymentCachingService;
    }

    @Override
    public PaymentOptionsDTO getPaymentOptions(String planId) {
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<PaymentOptionsDTO.PaymentGroupsDTO> paymentGroupsDTOS = new ArrayList<>();
        for (PaymentGroup group : paymentCachingService.getPaymentGroups().values()) {
            PlanDTO paidPlan = paymentCachingService.getPlan(planId);
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            Set<Integer> eligiblePlanIds = sessionDTO.get(ELIGIBLE_PLANS);
            Optional<Integer> freePlanOption = Optional.of(paidPlan.getLinkedFreePlanId());
            if (freePlanOption.isPresent() && !CollectionUtils.isEmpty(eligiblePlanIds) && eligiblePlanIds.contains(freePlanOption.get()) && paymentCachingService.getPlan(freePlanOption.get()).getPlanType() == PlanType.FREE_TRIAL && !group.getId().equalsIgnoreCase(CARD)) {
                continue;
            }
            List<PaymentMethodDTO> methodDTOS = availableMethods.get(group).stream().map(PaymentMethodDTO::new).collect(Collectors.toList());
            PaymentOptionsDTO.PaymentGroupsDTO groupsDTO = PaymentOptionsDTO.PaymentGroupsDTO.builder().paymentMethods(methodDTOS).paymentGroup(group.getId()).displayName(group.getDisplayName()).hierarchy(group.getHierarchy()).build();
            paymentGroupsDTOS.add(groupsDTO);
        }
        return PaymentOptionsDTO.builder().planDetails(buildPlanDetails(planId)).paymentGroups(paymentGroupsDTOS).build();
    }

    private PaymentOptionsDTO.PlanDetails buildPlanDetails(String planId) {
        boolean isFreeTrail = false;
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Set<Integer> eligiblePlanIds = sessionDTO.get(ELIGIBLE_PLANS);
        PlanDTO plan = paymentCachingService.getPlan(planId);
        if (plan.hasLinkedFreePlan() && !CollectionUtils.isEmpty(eligiblePlanIds)) {
            isFreeTrail = eligiblePlanIds.contains(plan.getLinkedFreePlanId());
        }
        OfferDTO offer = paymentCachingService.getOffer(plan.getLinkedOfferId());
        PartnerDTO partner = paymentCachingService.getPartner(!StringUtils.isEmpty(offer.getPackGroup()) ? offer.getPackGroup() : DEFAULT_PACK_GROUP.concat(offer.getService().toLowerCase()));
        return PaymentOptionsDTO.PlanDetails.builder()
                .validityUnit(plan.getPeriod().getValidityUnit())
                .perMonthValue(plan.getPrice().getMonthlyAmount())
                .discountedPrice(plan.getPrice().getAmount())
                .price(plan.getPrice().getDisplayAmount())
                .discount(plan.getPrice().getSavings())
                .partnerLogo(partner.getPartnerLogo())
                .month(plan.getPeriod().getMonth())
                .freeTrialAvailable(isFreeTrail)
                .partnerName(partner.getName())
                .build();
    }

    @Override
    public WynkResponseEntity.WynkBaseResponse<PaymentDetailsWrapper> getPaymentDetails(CombinedPaymentDetailsRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(UID);
        final String deviceId = sessionDTO.get(DEVICE_ID);
        ExecutorService executorService = Executors.newFixedThreadPool(N);
        Map<SavedDetailsKey, Future<WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails>>> map = new HashMap<>();
        Map<SavedDetailsKey, UserPreferredPayment> userPreferredPaymentMap = userPaymentsManager.get(uid).stream().collect(Collectors.toMap(UserPreferredPayment::getId, Function.identity()));
        Callable<WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails>> task;
        for (String paymentGroup : request.getPaymentGroups().keySet()) {
            for (String paymentCode : request.getPaymentGroups().get(paymentGroup)) {
                SavedDetailsKey.SavedDetailsKeyBuilder keyBuilder = SavedDetailsKey.builder().uid(uid).paymentCode(paymentCode).paymentGroup(paymentGroup);
                if (!userPreferredPaymentMap.containsKey(keyBuilder.build())) {
                    keyBuilder.deviceId(deviceId);
                }
                try {
                    IUserPreferredPaymentService userPreferredPaymentService = BeanLocatorFactory.getBean(PaymentCode.getFromPaymentCode(paymentCode).getCode(), IUserPreferredPaymentService.class);
                    String requestId = MDC.get(REQUEST_ID);
                    task = () -> {
                        MDC.put(REQUEST_ID, requestId);
                        return userPreferredPaymentService.getUserPreferredPayments(UserPreferredPaymentWrapper.builder().userPreferredPayment(userPreferredPaymentMap.getOrDefault(keyBuilder.build(), UserPreferredPayment.builder().id(keyBuilder.build()).build())).planId(request.getPlanId()).couponId(request.getCouponId()).build());
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
            WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<AbstractPaymentDetails>builder();

            Map<String, AbstractPaymentDetails> tempData;
            Map<String, Map<String, AbstractPaymentDetails>> paymentDetailsMap = new HashMap<>();

            Map<String, AbstractErrorDetails> tempError;
            Map<String, Map<String, AbstractErrorDetails>> paymentErrorMap = new HashMap<>();

            for (SavedDetailsKey savedDetailsKey : map.keySet()) {
                try {
                    WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> details = map.get(savedDetailsKey).get();

                    if (Objects.nonNull(details.getData())) {
                        tempData = paymentDetailsMap.getOrDefault(savedDetailsKey.getPaymentGroup(), new HashMap<>());
                        tempData.put(savedDetailsKey.getPaymentCode(), details.getData());
                        paymentDetailsMap.put(savedDetailsKey.getPaymentGroup(), tempData);
                    }

                    if (Objects.nonNull(details.getError())) {
                        tempError = paymentErrorMap.getOrDefault(savedDetailsKey.getPaymentGroup(), new HashMap<>());
                        tempError.put(savedDetailsKey.getPaymentCode(), (AbstractErrorDetails) details.getError());
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
                builder.data(PaymentDetailsWrapper.builder().details(paymentDetailsMap).build());
            }

            if (!paymentErrorMap.isEmpty()) {
                builder.error(CombinedStandardBusinessErrorDetails.builder().errors(paymentErrorMap).build()).success(false);
            }

            return builder.build();
        }
    }

}