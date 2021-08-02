package in.wynk.payment.service;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.FailureChargingStatusResponse;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static in.wynk.error.codes.core.constant.ErrorCodeConstants.FAIL001;
import static in.wynk.error.codes.core.constant.ErrorCodeConstants.FAIL002;
import static in.wynk.payment.core.constant.PaymentConstants.*;

public abstract class AbstractMerchantPaymentStatusService implements IMerchantPaymentStatusService<AbstractChargingStatusResponse, AbstractTransactionStatusRequest> {

    private final PaymentCachingService cachingService;
    private final IErrorCodesCacheService errorCodesCacheServiceImpl;

    protected AbstractMerchantPaymentStatusService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl) {
        this.cachingService = cachingService;
        this.errorCodesCacheServiceImpl = errorCodesCacheServiceImpl;
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
        final Transaction transaction = TransactionContext.get();
        final IChargingDetails.IPageUrlDetails pageUrlDetails = TransactionContext.getPurchaseDetails().map(details -> (IChargingDetails) details).map(IChargingDetails::getPageUrlDetails).orElseGet(() ->  {
            // NOTE: Added backward support to avoid failure for transaction created pre payment refactoring build, once the build is live it has no significance
            final SessionDTO session = SessionContextHolder.getBody();
            final IAppDetails appDetails = AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).appVersion(APP_VERSION).os(session.get(OS)).build();
            final String successPage = session.getSessionPayload().containsKey(SUCCESS_WEB_URL) ? session.get(SUCCESS_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.success.page}"), appDetails);
            final String failurePage = session.getSessionPayload().containsKey(FAILURE_WEB_URL) ? session.get(FAILURE_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.failure.page}"), appDetails);
            final String pendingPage = session.getSessionPayload().containsKey(PENDING_WEB_URL) ? session.get(PENDING_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.pending.page}"), appDetails);
            final String unknownPage = session.getSessionPayload().containsKey(UNKNOWN_WEB_URL) ? session.get(UNKNOWN_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.unknown.page}"), appDetails);
            return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
        });
        final TransactionStatus txnStatus = transaction.getStatus();
        if (EnumSet.of(TransactionStatus.FAILURE, TransactionStatus.FAILUREALREADYSUBSCRIBED).contains(txnStatus)) {
            return failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL001), transaction, request, pageUrlDetails.getFailurePageUrl());
        } else if (txnStatus == TransactionStatus.INPROGRESS) {
            return failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL002), transaction, request, pageUrlDetails.getPendingPageUrl());
        } else {
            ChargingStatusResponse.ChargingStatusResponseBuilder<?,?> builder = ChargingStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).planId(request.getPlanId()).validity(cachingService.validTillDate(request.getPlanId()));
            if (txnStatus == TransactionStatus.SUCCESS) {
                builder.packDetails(getPackDetails(transaction, request));
                builder.redirectUrl(pageUrlDetails.getSuccessPageUrl());
            }
            return WynkResponseEntity. < AbstractChargingStatusResponse > builder().data(builder.build()).build();
        }
    }

    private WynkResponseEntity<AbstractChargingStatusResponse> failure(ErrorCode errorCode, Transaction transaction, ChargingTransactionStatusRequest request, String redirectUrl) {
        final Optional<String> subtitle = errorCode.getMeta(SUBTITLE_TEXT);
        final Optional<String> buttonText = errorCode.getMeta(BUTTON_TEXT);
        final Optional<Boolean> buttonArrow = errorCode.getMeta(BUTTON_ARROW);
        final FailureChargingStatusResponse failureChargingStatusResponse = FailureChargingStatusResponse.populate(errorCode, subtitle.orElse(""),buttonText.orElse(""),buttonArrow.orElse(Boolean.FALSE), transaction.getIdStr(), request.getPlanId(), getPackDetails(transaction, request), transaction.getStatus(), redirectUrl);
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(failureChargingStatusResponse).build();
    }

    private AbstractPack getPackDetails(Transaction transaction,ChargingTransactionStatusRequest request) {
        final PlanDTO plan = cachingService.getPlan(request.getPlanId());
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        final PartnerDTO partner = cachingService.getPartner(Optional.ofNullable(offer.getPackGroup()).orElse(BaseConstants.DEFAULT_PACK_GROUP + offer.getService()));
        if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            final PlanDTO paidPlan = cachingService.getPlan(transaction.getPlanId());
            final TrialPack.TrialPackBuilder<?, ?> trialPackBuilder = TrialPack.builder().title(offer.getTitle()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> bundleBenefitsBuilder = BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                trialPackBuilder.benefits(bundleBenefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                trialPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            return trialPackBuilder.paidPack(PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name()).month(paidPlan.getPeriod().getMonth()).monthlyAmount(paidPlan.getPrice().getMonthlyAmount()).build()).build();
        } else {
            PaidPack.PaidPackBuilder<?, ?> paidPackBuilder = PaidPack.builder().title(offer.getTitle()).amount(plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name()).month(plan.getPeriod().getMonth()).monthlyAmount(plan.getPrice().getMonthlyAmount()).dailyAmount(plan.getPrice().getDailyAmount()).day(plan.getPeriod().getDay());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder = BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
                paidPackBuilder.benefits(benefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                paidPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            return paidPackBuilder.build();
        }
    }

    private String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

}
