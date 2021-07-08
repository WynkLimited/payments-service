package in.wynk.payment.service;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.FailureChargingStatusResponse;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;

public abstract class AbstractMerchantPaymentStatusService implements IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> {

    private final PaymentCachingService cachingService;

    protected AbstractMerchantPaymentStatusService(PaymentCachingService cachingService) {
        this.cachingService = cachingService;
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionStatusRequest transactionStatusRequest) {
        if (AbstractTransactionReconciliationStatusRequest.class.isAssignableFrom(transactionStatusRequest.getClass())) {
            return status((AbstractTransactionReconciliationStatusRequest) transactionStatusRequest);
        } else if (ChargingTransactionStatusRequest.class.isAssignableFrom(transactionStatusRequest.getClass())) {
            return status((ChargingTransactionStatusRequest) transactionStatusRequest);
        } else {
            throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
    }

    public abstract WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest);

    public WynkResponseEntity<AbstractChargingStatusResponse> status(ChargingTransactionStatusRequest request) {
        Transaction transaction = TransactionContext.get();
        IChargingDetails.IPageUrlDetails pageUrlDetails = TransactionContext.getPurchaseDetails().map(details -> (IChargingDetails) details).map(IChargingDetails::getPageUrlDetails).orElseThrow(() -> new WynkRuntimeException("Pages are not configured"));
        TransactionStatus txnStatus = transaction.getStatus();
        if (txnStatus == TransactionStatus.FAILURE) {
            return failure(ErrorCode.FAIL001,FAIL001_ERROR_MAP, transaction, request, pageUrlDetails.getFailurePageUrl());
        } else if (txnStatus == TransactionStatus.INPROGRESS) {
            return failure(ErrorCode.FAIL002,FAIL002_ERROR_MAP, transaction, request, pageUrlDetails.getPendingPageUrl());
        } else {
            ChargingStatusResponse.ChargingStatusResponseBuilder<?,?> builder = ChargingStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).planId(request.getPlanId()).validity(cachingService.validTillDate(request.getPlanId()));
            if (txnStatus == TransactionStatus.SUCCESS) {
                builder.packDetails(getPackDetails(transaction, request));
                builder.redirectUrl(pageUrlDetails.getSuccessPageUrl());
            }
            return WynkResponseEntity. < AbstractChargingStatusResponse > builder().data(builder.build()).build();
        }
    }

    private WynkResponseEntity<AbstractChargingStatusResponse> failure(ErrorCode errorCode, Map<String,String> errorMap,Transaction transaction,ChargingTransactionStatusRequest request, String redirectUrl) {
        FailureChargingStatusResponse failureChargingStatusResponse = FailureChargingStatusResponse.populate(errorCode, errorMap.get(SUBTITLE_TEXT),errorMap.get(BUTTON_TEXT),Boolean.parseBoolean(errorMap.get(BUTTON_ARROW)), transaction.getIdStr(), request.getPlanId(), getPackDetails(transaction, request), transaction.getStatus(), redirectUrl);
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(failureChargingStatusResponse).build();
    }

    private AbstractPack getPackDetails(Transaction transaction,ChargingTransactionStatusRequest request) {

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
            return trialPackBuilder.paidPack(PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name()).month(paidPlan.getPeriod().getMonth()).monthlyAmount(paidPlan.getPrice().getMonthlyAmount()).build()).build();
        } else {
            PaidPack.PaidPackBuilder<?, ?> paidPackBuilder = PaidPack.builder().title(offer.getTitle()).amount(plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name()).month(plan.getPeriod().getMonth()).monthlyAmount(plan.getPrice().getMonthlyAmount());
            if (offer.isCombo()) {
                BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder = BundleBenefits.builder().name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                paidPackBuilder.benefits(benefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                paidPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            return paidPackBuilder.build();
        }
    }

}
