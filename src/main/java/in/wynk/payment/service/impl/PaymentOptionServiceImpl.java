package in.wynk.payment.service.impl;

import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
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

    private static final int N=3;
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
                .freeTrialAvailable(isFreeTrail)
                .partnerName(partner.getName())
                .build();
    }

    @Override
    public PaymentDetailsWrapper getPaymentDetails(String planId, List<PaymentCode> codes) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        PaymentDetailsWrapper paymentDetailsWrapper = new PaymentDetailsWrapper();
        ExecutorService executorService = Executors.newFixedThreadPool(N);
        Map<String, Future<AbstractPaymentDetails>> map = new HashMap<>();
        Callable<AbstractPaymentDetails> task;
        for (PaymentCode paymentCode : codes) {
            IUserPreferredPaymentService userPreferredPaymentService = BeanLocatorFactory.getBean(paymentCode.getCode(), IUserPreferredPaymentService.class);
            task = () -> userPreferredPaymentService.getUserPreferredPayments(sessionDTO.get(UID), planId, sessionDTO.get(DEVICE_ID));
            map.put(paymentCode.getCode(), executorService.submit(task));
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(N, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } finally {
            Map<String, AbstractPaymentDetails> paymentDetailsMap = paymentDetailsWrapper.getDetails();
            for (String paymentCode: map.keySet()) {
                try {
                    paymentDetailsMap.put(paymentCode, map.get(paymentCode).get());
                } catch (Exception e) {}
            }
            return paymentDetailsWrapper;
        }
    }

}