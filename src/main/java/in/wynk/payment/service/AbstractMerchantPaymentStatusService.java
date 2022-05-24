package in.wynk.payment.service;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
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
import in.wynk.payment.core.dao.entity.IChargingDetails;
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
import in.wynk.subscription.common.dto.ProductDTO;

import java.util.*;
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
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(transaction.getClientAlias());
            final String clientPagePlaceHolder = PAYMENT_PAGE_PLACE_HOLDER.replace("%c", clientAlias);
            final IAppDetails appDetails = AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).appVersion(APP_VERSION).os(session.get(OS)).build();
            final String successPage = session.getSessionPayload().containsKey(SUCCESS_WEB_URL) ? session.get(SUCCESS_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"),"${payment.success.page}"), appDetails);
            final String failurePage = session.getSessionPayload().containsKey(FAILURE_WEB_URL) ? session.get(FAILURE_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}"), appDetails);
            final String pendingPage = session.getSessionPayload().containsKey(PENDING_WEB_URL) ? session.get(PENDING_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "pending"), "${payment.pending.page}"), appDetails);
            final String unknownPage = session.getSessionPayload().containsKey(UNKNOWN_WEB_URL) ? session.get(UNKNOWN_WEB_URL): buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "unknown"), "${payment.unknown.page}"), appDetails);
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
        AbstractPack abstractPack= null;
        if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            final PlanDTO paidPlan = cachingService.getPlan(transaction.getPlanId());
            final TrialPack.TrialPackBuilder<?, ?> trialPackBuilder = TrialPack.builder().title(offer.getTitle()).day(plan.getPeriod().getDay()).amount(plan.getFinalPrice()).month(plan.getPeriod().getMonth()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name()).currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> bundleBenefitsBuilder = BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).notVisible(channelPartner.isNotVisible()).packGroup(channelPartner.getPackGroup()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());

                if(request.getAppId() != null && !request.getAppId().isEmpty()){

                    Set<String> packgroupAppIdHierarchySet = getPackgroupAppIdHierarchySet(request.getAppId().toUpperCase());

                  for(int i =0; i <channelBenefits.size(); i++){
                      ChannelBenefits channelBenefit = channelBenefits.get(i);
                     if(!channelBenefit.isNotVisible() && channelBenefit.getPackGroup() != null && packgroupAppIdHierarchySet.contains(channelBenefit.getPackGroup()))
                         channelBenefit.setNotVisible(true);
                   }
                 }
                trialPackBuilder.benefits(bundleBenefitsBuilder.channelsBenefits(channelBenefits).build());

            }else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).notVisible(partner.isNotVisible()).packGroup(partner.getPackGroup()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                trialPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            abstractPack = trialPackBuilder.paidPack(PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name()).month(paidPlan.getPeriod().getMonth()).perMonthValue(paidPlan.getPrice().getMonthlyAmount()).day(paidPlan.getPeriod().getDay()).dailyAmount(paidPlan.getPrice().getDailyAmount()).currency(paidPlan.getPrice().getCurrency()).build()).isCombo(offer.isCombo()).build();
        } else {
            PaidPack.PaidPackBuilder<?, ?> paidPackBuilder = PaidPack.builder().title(offer.getTitle()).amount(plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name()).month(plan.getPeriod().getMonth()).perMonthValue(plan.getPrice().getMonthlyAmount()).dailyAmount(plan.getPrice().getDailyAmount()).day(plan.getPeriod().getDay()).currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder = BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner).map(channelPartner -> ChannelBenefits.builder().packGroup(channelPartner.getPackGroup()).name(channelPartner.getName()).notVisible(channelPartner.isNotVisible()).icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());

                if(request.getAppId() != null && !request.getAppId().isEmpty()){

                    Set<String> packgroupAppIdHierarchySet = getPackgroupAppIdHierarchySet(request.getAppId().toUpperCase());

                    for(int i =0; i <channelBenefits.size(); i++){
                        ChannelBenefits channelBenefit = channelBenefits.get(i);
                        if(!channelBenefit.isNotVisible() && channelBenefit.getPackGroup() != null && packgroupAppIdHierarchySet.contains(channelBenefit.getPackGroup()))
                            channelBenefit.setNotVisible(true);
                    }
                 }
                paidPackBuilder.benefits(benefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder = ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).packGroup(partner.getPackGroup()).notVisible(partner.isNotVisible()).icon(partner.getIcon()).logo(partner.getLogo()).rails(partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
                paidPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            abstractPack = paidPackBuilder.build();
        }
        return abstractPack;
    }

    private Set<String> getPackgroupAppIdHierarchySet(String appId){
        Set<String> packgroupAppIdHierarchySet = new HashSet<>();
        for( String productId : cachingService.getProducts().keySet()) {
            ProductDTO product = cachingService.getProducts().get(productId);
            if (product.getAppIdHierarchy() != null && product.getAppIdHierarchy().size() >0 && product.getAppIdHierarchy().containsKey(appId) && product.getAppIdHierarchy().get(appId) == -5)
                packgroupAppIdHierarchySet.add(product.getPackGroup());
        }
        return packgroupAppIdHierarchySet;
    }


    private String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

}
