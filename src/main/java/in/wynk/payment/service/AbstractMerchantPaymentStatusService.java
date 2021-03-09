package in.wynk.payment.service;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractMerchantPaymentStatusService implements IMerchantPaymentStatusService {

    private final PaymentCachingService cachingService;

    protected AbstractMerchantPaymentStatusService(PaymentCachingService cachingService) {
        this.cachingService = cachingService;
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionStatusRequest transactionStatusRequest) {
        if (AbstractTransactionReconciliationStatusRequest.class.isAssignableFrom(transactionStatusRequest.getClass())) {
            return status((AbstractTransactionReconciliationStatusRequest) transactionStatusRequest);
        } else if (ChargingTransactionStatusRequest.class.isAssignableFrom(transactionStatusRequest.getClass())) {
            return status((ChargingTransactionStatusRequest) transactionStatusRequest);
        } else {
            throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
    }

    public abstract BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest);

    public BaseResponse<ChargingStatusResponse> status(ChargingTransactionStatusRequest request) {
        Transaction transaction = TransactionContext.get();
        ChargingStatusResponse.ChargingStatusResponseBuilder builder = ChargingStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).planId(request.getPlanId()).validity(cachingService.validTillDate(request.getPlanId()));

        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            PlanDTO plan = cachingService.getPlan(request.getPlanId());
            OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            PartnerDTO partner = cachingService.getPartner(Optional.ofNullable(offer.getPackGroup()).orElse(BaseConstants.DEFAULT_PACK_GROUP + offer.getService()));
            if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
                PlanDTO paidPlan = cachingService.getPlan(transaction.getPlanId());
                TrialPack.TrialPackBuilder<?, ?> trialPackBuilder = TrialPack.builder().title(offer.getTitle()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name());
                if (offer.isCombo()) {
                    BundleBenefits.BundleBenefitsBuilder<?, ?> bundleBenefitsBuilder = BundleBenefits.builder().name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                    List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                    trialPackBuilder.benefits(bundleBenefitsBuilder.channelsBenefits(channelBenefits).build());
                } else {
                    ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                    trialPackBuilder.benefits(channelBenefitsBuilder.build());
                }
                builder.packDetails(trialPackBuilder.paidPack(PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name()).build()).build());
            } else {
                PaidPack.PaidPackBuilder<?, ?> paidPackBuilder = PaidPack.builder().title(offer.getTitle()).amount(plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name());
                if (offer.isCombo()) {
                    BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder = BundleBenefits.builder().name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                    List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                    paidPackBuilder.benefits(benefitsBuilder.channelsBenefits(channelBenefits).build());
                } else {
                    ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                    paidPackBuilder.benefits(channelBenefitsBuilder.build());
                }
                builder.packDetails(paidPackBuilder.build());
            }
        }
        return BaseResponse.<ChargingStatusResponse>builder().body(builder.build()).status(HttpStatus.OK).build();
    }

}
