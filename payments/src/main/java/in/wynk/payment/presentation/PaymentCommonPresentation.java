package in.wynk.payment.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PresentationUtils;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentCommonPresentation {
    private final PaymentCachingService cachingService;
    private final ObjectMapper objectMapper;

    public AbstractPack getPackDetails(Transaction transaction, ChargingTransactionStatusRequest request) {
        final PlanDTO plan = cachingService.getPlan(request.getPlanId());
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        final PartnerDTO partner = cachingService.getPartner(Optional.ofNullable(offer.getPackGroup()).orElse(BaseConstants.DEFAULT_PACK_GROUP + offer.getService()));
        if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            final PlanDTO paidPlan = cachingService.getPlan(transaction.getPlanId());
            final TrialPack.TrialPackBuilder<?, ?> trialPackBuilder = TrialPack.builder().title(offer.getTitle()).day(plan.getPeriod().getDay()).amount(plan.getFinalPrice()).month(plan.getPeriod().getMonth()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name()).currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> bundleBenefitsBuilder = BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner,offer));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                trialPackBuilder.benefits(bundleBenefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner,offer));
                trialPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            return trialPackBuilder.paidPack(PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name()).month(paidPlan.getPeriod().getMonth()).perMonthValue((int) paidPlan.getPrice().getMonthlyAmount()).day(paidPlan.getPeriod().getDay()).dailyAmount(paidPlan.getPrice().getDailyAmount()).currency(paidPlan.getPrice().getCurrency()).build()).isCombo(offer.isCombo()).build();
        } else {
            PaidPack.PaidPackBuilder<?, ?> paidPackBuilder = PaidPack.builder().title(offer.getTitle()).amount(plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name()).month(plan.getPeriod().getMonth()).perMonthValue((int) plan.getPrice().getMonthlyAmount()).dailyAmount(plan.getPrice().getDailyAmount()).day(plan.getPeriod().getDay()).currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder = BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner,offer));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                paidPackBuilder.benefits(benefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner,offer));
                paidPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            return paidPackBuilder.build();
        }
    }

    public PurchaseDetailsDto getPurchaseDetails(IPurchaseDetails purchaseDetails) {
        return PurchaseDetailsDto.builder()
                .appDetails(from(purchaseDetails.getAppDetails()))
                .productDetails(from(purchaseDetails.getProductDetails()))
                .paymentDetails(from(purchaseDetails.getPaymentDetails()))
                .userDetails(from(purchaseDetails.getUserDetails()))
                .build();

    }

    private AppDetailsDto from(IAppDetails appDetails) {
        return AppDetailsDto.builder()
                .os(appDetails.getOs())
                .buildNo(appDetails.getBuildNo())
                .deviceId(appDetails.getDeviceId()).build();
    }

    private ProductDetailsDto from(IProductDetails productDetails) {
        return ProductDetailsDto.builder().planId(productDetails.getId()).type(productDetails.getType()).build();
    }

    private PaymentDetailsDto from(IPaymentDetails paymentDetails) {
        return PaymentDetailsDto.builder()
                .autoRenew(paymentDetails.isAutoRenew())
                .paymentId(paymentDetails.getPaymentId())
                .paymentMode(paymentDetails.getPaymentMode())
                .couponId(paymentDetails.getCouponId())
                .merchantName(paymentDetails.getMerchantName())
                .trialOpted(paymentDetails.isTrialOpted())
                .build();
    }

    private UserDetailsDto from(IUserDetails userDetails) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(userDetails), UserDetailsDto.class);
        } catch (Exception exc) {
            return null;
        }

    }

}
