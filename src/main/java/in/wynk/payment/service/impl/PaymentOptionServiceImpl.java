package in.wynk.payment.service.impl;

import in.wynk.common.dto.CombinedStandardBusinessErrorDetails;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.request.CombinedPaymentDetailsRequest;
import in.wynk.payment.dto.request.UserPreferredPaymentsRequest;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.PaymentDetailsWrapper;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.IUserPreferredPaymentService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;

@Service
public class PaymentOptionServiceImpl implements IPaymentOptionService {

    private static final int N=8;
    private final PaymentCachingService paymentCachingService;

    public PaymentOptionServiceImpl(PaymentCachingService paymentCachingService) {
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
        if(plan.hasLinkedFreePlan() && !CollectionUtils.isEmpty(eligiblePlanIds)) {
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
        Map<Key, Future<WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails>>> map = new HashMap<>();
        Callable<WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails>> task;
        for (String paymentGroup : request.getPaymentGroups().keySet()) {
            for (String paymentCode : request.getPaymentGroups().get(paymentGroup)) {
                IUserPreferredPaymentService userPreferredPaymentService = BeanLocatorFactory.getBean(PaymentCode.getFromPaymentCode(paymentCode).getCode(), IUserPreferredPaymentService.class);
                task = () -> userPreferredPaymentService.getUserPreferredPayments(UserPreferredPaymentsRequest.builder()
                        .uid(uid)
                        .deviceId(deviceId)
                        .paymentCode(paymentCode)
                        .paymentGroup(paymentGroup)
                        .planId(request.getPlanId())
                        .build());
                map.put(Key.builder().uid(uid).paymentCode(paymentCode).paymentGroup(paymentGroup).build(), executorService.submit(task));
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

            Map<String, StandardBusinessErrorDetails> tempError;
            Map<String, Map<String, StandardBusinessErrorDetails>> paymentErrorMap = new HashMap<>();

            for (Key key: map.keySet()) {
                try {
                    WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> details = map.get(key).get();

                    if (Objects.nonNull(details.getData())) {
                        tempData = paymentDetailsMap.getOrDefault(key.getPaymentGroup(), new HashMap<>());
                        tempData.put(key.getPaymentCode(), details.getData());
                        paymentDetailsMap.put(key.getPaymentGroup(), tempData);
                    }

                    if (Objects.nonNull(details.getError())) {
                        tempError = paymentErrorMap.getOrDefault(key.getPaymentGroup(), new HashMap<>());
                        tempError.put(key.getPaymentCode(), (StandardBusinessErrorDetails) details.getError());
                        paymentErrorMap.put(key.getPaymentGroup(), tempError);
                    }
                } catch (Exception e) {}
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