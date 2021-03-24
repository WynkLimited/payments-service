package in.wynk.payment.service.impl;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO.PaymentMethodDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;

@Service
public class PaymentOptionServiceImpl implements IPaymentOptionService {

    private static final int N=2;
    @Autowired
    private PaymentCachingService paymentCachingService;
    @Autowired
    private PayUMerchantPaymentService payUMerchantPaymentService;
    @Autowired
    private PaytmMerchantWalletPaymentService paytmMerchantWalletPaymentService;

    @Override
    public PaymentOptionsDTO getPaymentOptions(String planId) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        Map<String, List<PaymentMethod>> availableMethods = paymentCachingService.getGroupedPaymentMethods();
        List<UserPreferredPayment> preferredPayments = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(N);
        Callable<UserPreferredPayment> task1 = () -> paytmMerchantWalletPaymentService.getUserPreferredPayments(uid);
        Callable<UserPreferredPayment> task2 = () -> payUMerchantPaymentService.getUserPreferredPayments(uid);
        Future<UserPreferredPayment> preferredPaymentsCard = executorService.submit(task1);
        Future<UserPreferredPayment> preferredPaymentsWallet = executorService.submit(task2);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(N, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (Exception e) { }
        finally {
            try {
                preferredPayments.add(preferredPaymentsCard.get());
            } catch (Exception e) { }
            try {
                preferredPayments.add(preferredPaymentsWallet.get());
            } catch (Exception e) { }
        }
        return buildPaymentOptions(planId, availableMethods, preferredPayments);
    }

    private PaymentOptionsDTO buildPaymentOptions(String planId, Map<String, List<PaymentMethod>> availableMethods, List<UserPreferredPayment> preferredPayments) {
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
            for (PaymentMethodDTO paymentMethodDTO : methodDTOS) {
                String paymentGroup = paymentMethodDTO.getGroup();
                String paymentCode = paymentMethodDTO.getPaymentCode();
                Map<String, Object> meta = paymentMethodDTO.getMeta();
                List<UserPreferredPayment> savedPayments = preferredPayments.parallelStream().filter(x -> x != null && x.getOption().getGroup().toString().equals(paymentGroup) && x.getOption().getPaymentCode().toString().equals(paymentCode)).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(savedPayments)) {
                    meta.put("savedPayments", savedPayments);
                }
            }
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

}
