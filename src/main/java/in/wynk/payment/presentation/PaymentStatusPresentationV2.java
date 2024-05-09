package in.wynk.payment.presentation;

import com.google.common.base.Strings;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.presentation.dto.status.FailurePaymentStatusResponse;
import in.wynk.payment.presentation.dto.status.PaymentStatusResponse;
import in.wynk.payment.presentation.dto.status.SuccessPaymentStatusResponse;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PresentationUtils;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.spel.IRuleEvaluator;
import in.wynk.spel.builder.DefaultStandardExpressionContextBuilder;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.error.codes.core.constant.ErrorCodeConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStatusPresentationV2 implements IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse> {

    private final IRuleEvaluator ruleEvaluator;
    private final PaymentCachingService cachingService;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final IErrorCodesCacheService errorCodesCacheServiceImpl;
    private final Map<String, IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse>> delegate = new HashMap<>();

    @PostConstruct
    public void init() {
        delegate.put(ADD_TO_BILL, new AddToBillPaymentStatusHandler());
        delegate.put("GENERIC", new GenericPaymentStatusHandler());
    }

    private class AddToBillPaymentStatusHandler implements IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse> {

        @SneakyThrows
        @Override
        public PaymentStatusResponse transform (AbstractPaymentStatusResponse payload) {
            final Transaction transaction = TransactionContext.get();
            final IChargingDetails.IPageUrlDetails pageUrlDetails = getPageUrlDetails(transaction);
            final TransactionStatus txnStatus = transaction.getStatus();
            final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
            if (EnumSet.of(TransactionStatus.FAILURE, TransactionStatus.FAILUREALREADYSUBSCRIBED).contains(txnStatus)) {
                return failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL003), transaction, pageUrlDetails.getFailurePageUrl(), paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup());
            } else if (txnStatus == TransactionStatus.INPROGRESS) {
                return failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL004), transaction, pageUrlDetails.getPendingPageUrl(), paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup());
            } else {
                SuccessPaymentStatusResponse.SuccessPaymentStatusResponseBuilder<?,?> builder = SuccessPaymentStatusResponse.builder()
                        .transactionStatus(payload.getTransactionStatus()).planId(transaction.getPlanId())
                        .tid(payload.getTid()).transactionType(payload.getTransactionType())
                        .validity(cachingService.validTillDate(transaction.getPlanId(),transaction.getMsisdn()))
                        .paymentGroup(paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup());
                if (txnStatus == TransactionStatus.SUCCESS) {
                    builder.packDetails(getPackDetails(transaction));
                    builder.redirectUrl(pageUrlDetails.getSuccessPageUrl());
                    return successATB(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(ATBSUCCESS001), builder, transaction);
                }
                return builder.build();
            }
        }

        private PaymentStatusResponse successATB (ErrorCode errorCode, SuccessPaymentStatusResponse.SuccessPaymentStatusResponseBuilder<?,?> builder, Transaction transaction) {
            final Optional<String> subtitle = errorCode.getMeta(SUBTITLE_TEXT);
            final Optional<String> buttonText = errorCode.getMeta(BUTTON_TEXT);
            final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
            final String validityText = (plan.getPeriod().getValidity() <= 7) ?
                    "weekly" : (plan.getPeriod().getValidity() <= 30) ?
                    "monthly" : (plan.getPeriod().getValidity() <= 90) ?
                    "quarterly" : (plan.getPeriod().getValidity() <= 365) ?
                    "yearly" : "";
            final StandardEvaluationContext seContext = DefaultStandardExpressionContextBuilder.builder()
                    .variable(PLAN, plan)
                    .variable("SI", transaction.getMsisdn())
                    .variable("VALIDITY", validityText)
                    .build();
            final String evaluatedMessage = ruleEvaluator.evaluate(subtitle.orElse(errorCode.getExternalMessage()),
                    () -> seContext, SMS_MESSAGE_TEMPLATE_CONTEXT, String.class);
            builder.subtitle(evaluatedMessage);
            builder.buttonText(buttonText.orElse(""));
            builder.title(errorCode.getExternalMessage());
            return builder.build();
        }
    }

    private class GenericPaymentStatusHandler implements IWynkPresentation<PaymentStatusResponse, AbstractPaymentStatusResponse> {

        @SneakyThrows
        @Override
        public PaymentStatusResponse transform (AbstractPaymentStatusResponse payload) {
            final Transaction transaction = TransactionContext.get();
            final IChargingDetails.IPageUrlDetails pageUrlDetails = getPageUrlDetails(transaction);
            final TransactionStatus txnStatus = transaction.getStatus();
            final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
            if (EnumSet.of(TransactionStatus.FAILURE, TransactionStatus.FAILUREALREADYSUBSCRIBED).contains(txnStatus)) {
                return PaymentEvent.MANDATE == transaction.getType() ? failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL005), transaction, pageUrlDetails.getFailurePageUrl(),
                        paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup()) :
                        failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL003), transaction, pageUrlDetails.getFailurePageUrl(),
                                paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup());
            } else if (txnStatus == TransactionStatus.INPROGRESS) {
                return PaymentEvent.MANDATE == transaction.getType() ?
                        failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(PENDING001), transaction, pageUrlDetails.getPendingPageUrl(),
                                paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup()) :
                        failure(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(FAIL004), transaction, pageUrlDetails.getPendingPageUrl(),
                                paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup());
            } else {
                SuccessPaymentStatusResponse.SuccessPaymentStatusResponseBuilder<?, ?> builder = SuccessPaymentStatusResponse.builder()
                        .transactionStatus(payload.getTransactionStatus())
                        .tid(payload.getTid()).transactionType(payload.getTransactionType())
                        .paymentGroup(paymentMethodCachingService.get(purchaseDetails.getPaymentDetails().getPaymentId()).getGroup());
                if (transaction.getType() == PaymentEvent.POINT_PURCHASE) {
                    builder.itemId(transaction.getItemId());
                } else {
                    builder.validity(cachingService.validTillDate(transaction.getPlanId(), transaction.getMsisdn()));
                    builder.planId(transaction.getPlanId());
                }
                if (txnStatus == TransactionStatus.SUCCESS) {
                    builder.packDetails(getPackDetails(transaction));
                    builder.redirectUrl(pageUrlDetails.getSuccessPageUrl());
                    return PaymentEvent.MANDATE == transaction.getType() ? success(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(SUCCESS002), builder) :
                            success(errorCodesCacheServiceImpl.getErrorCodeByInternalCode(SUCCESS001), builder);
                }
                return builder.build();
            }
        }

        private PaymentStatusResponse success (ErrorCode errorCode, SuccessPaymentStatusResponse.SuccessPaymentStatusResponseBuilder<?,?> builder) {
            final Optional<String> subtitle = errorCode.getMeta(SUBTITLE_TEXT);
            final Optional<String> buttonText = errorCode.getMeta(BUTTON_TEXT);
            builder.subtitle(subtitle.orElse(""));
            builder.buttonText(buttonText.orElse(""));
            builder.title(errorCode.getExternalMessage());
            return builder.build();
        }
    }

    @SneakyThrows
    @Override
    @TransactionAware(txnId = "#payload.tid")
    public PaymentStatusResponse transform (AbstractPaymentStatusResponse payload) {
        final Transaction transaction = TransactionContext.get();
        return delegate.getOrDefault(transaction.getPaymentChannel().getId(), delegate.get("GENERIC")).transform(payload);
    }

    private IChargingDetails.IPageUrlDetails getPageUrlDetails (Transaction transaction) {
        final IChargingDetails.IPageUrlDetails pageUrlDetails = TransactionContext.getPurchaseDetails().map(details -> (IChargingDetails) details).map(IChargingDetails::getPageUrlDetails).orElseGet(() ->  {
            // NOTE: Added backward support to avoid failure for transaction created pre-payment refactoring build, once the build is live it has no significance
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(transaction.getClientAlias());
            String service = null;
            if (Objects.isNull(transaction.getPlanId())) {
                service = clientAlias.equalsIgnoreCase("music") ? "music" : "airteltv";
            } else {
                service = cachingService.getPlan(transaction.getPlanId()).getService();
            }

            final String clientPagePlaceHolder = PAYMENT_PAGE_PLACE_HOLDER.replace("%c", clientAlias);
            final IAppDetails appDetails = AppDetails.builder().buildNo(-1).service(service).appId(MOBILITY).os(ANDROID).build();
            final String successPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"),"${payment.success.page}"), appDetails);
            final String failurePage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}"), appDetails);
            final String pendingPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "pending"), "${payment.pending.page}"), appDetails);
            final String unknownPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "unknown"), "${payment.unknown.page}"), appDetails);
            return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
        });
        return pageUrlDetails;
    }

    private String buildUrlFrom (String url, IAppDetails appDetails) {
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

    private PaymentStatusResponse failure (ErrorCode errorCode, Transaction transaction, String redirectUrl, String paymentGroup) {
        final Optional<String> subtitle = errorCode.getMeta(SUBTITLE_TEXT);
        final Optional<String> buttonText = errorCode.getMeta(BUTTON_TEXT);
        final Optional<Boolean> buttonArrow = errorCode.getMeta(BUTTON_ARROW);
        return FailurePaymentStatusResponse.populate(errorCode, subtitle.orElse(""),buttonText.orElse(""),buttonArrow.orElse(Boolean.FALSE), transaction.getIdStr(), transaction.getPlanId(), getPackDetails(transaction), transaction.getStatus(), redirectUrl, paymentGroup, transaction.getItemId());
    }

    private AbstractPack getPackDetails (Transaction transaction) {
        if (Objects.nonNull(transaction.getItemId())) {
            return null;
        }
        final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        final PartnerDTO partner = cachingService.getPartner(Optional.ofNullable(offer.getPackGroup()).orElse(BaseConstants.DEFAULT_PACK_GROUP + offer.getService()));
        AbstractPack abstractPack;
        String appId = null;
        final Optional<IPurchaseDetails> purchaseDetails = TransactionContext.getPurchaseDetails();
        if (purchaseDetails.isPresent() && purchaseDetails.get().getAppDetails() != null) {
            appId = purchaseDetails.get().getAppDetails().getAppId();
        }
        if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            final PlanDTO paidPlan = cachingService.getPlan(transaction.getPlanId());
            final TrialPack.TrialPackBuilder<?, ?> trialPackBuilder =
                    TrialPack.builder().title(offer.getTitle()).day(plan.getPeriod().getDay()).amount(plan.getFinalPrice()).month(plan.getPeriod().getMonth()).period(plan.getPeriod().getValidity())
                            .timeUnit(plan.getPeriod().getTimeUnit().name()).currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> bundleBenefitsBuilder =
                        BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(
                                PresentationUtils.getRails(partner, offer));
                final List<ChannelBenefits>
                        channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner)
                        .map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).notVisible(channelPartner.isNotVisible()).packGroup(channelPartner.getPackGroup())
                                .icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(
                                Collectors.toList());
                if (!Strings.isNullOrEmpty(appId)) {
                    Set<String> packGroupAppIdHierarchySet = getPackGroupAppIdHierarchySet(appId.toUpperCase());
                    channelBenefits.forEach(channelBenefit -> {
                        if (!channelBenefit.isNotVisible() && channelBenefit.getPackGroup() != null && packGroupAppIdHierarchySet.contains(channelBenefit.getPackGroup())) {
                            channelBenefit.setNotVisible(true);
                        }
                    });
                }
                trialPackBuilder.benefits(bundleBenefitsBuilder.channelsBenefits(channelBenefits).build());

            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder =
                        ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).notVisible(partner.isNotVisible()).packGroup(partner.getPackGroup()).icon(partner.getIcon())
                                .logo(partner.getLogo()).rails(PresentationUtils.getRails(partner, offer));
                trialPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            abstractPack = trialPackBuilder.paidPack(
                    PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name())
                            .month(paidPlan.getPeriod().getMonth()).perMonthValue((int) paidPlan.getPrice().getMonthlyAmount()).day(paidPlan.getPeriod().getDay())
                            .dailyAmount(paidPlan.getPrice().getDailyAmount()).currency(paidPlan.getPrice().getCurrency()).build()).isCombo(offer.isCombo()).build();
        } else {
            PaidPack.PaidPackBuilder<?, ?> paidPackBuilder =
                    PaidPack.builder().title(offer.getTitle()).amount((int) plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name())
                            .month(plan.getPeriod().getMonth()).perMonthValue((int) plan.getPrice().getMonthlyAmount()).dailyAmount(plan.getPrice().getDailyAmount()).day(plan.getPeriod().getDay())
                            .currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder =
                        BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner, offer));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner)
                        .map(channelPartner -> ChannelBenefits.builder().packGroup(channelPartner.getPackGroup()).name(channelPartner.getName()).notVisible(channelPartner.isNotVisible())
                                .icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());

                if (!Strings.isNullOrEmpty(appId)) {
                    Set<String> packGroupAppIdHierarchySet = getPackGroupAppIdHierarchySet(appId.toUpperCase());
                    channelBenefits.forEach(channelBenefit -> {
                        if (!channelBenefit.isNotVisible() && channelBenefit.getPackGroup() != null && packGroupAppIdHierarchySet.contains(channelBenefit.getPackGroup())) {
                            channelBenefit.setNotVisible(true);
                        }
                    });
                }
                paidPackBuilder.benefits(benefitsBuilder.channelsBenefits(channelBenefits).build());
            } else {
                final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder =
                        ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).packGroup(partner.getPackGroup()).notVisible(partner.isNotVisible()).icon(partner.getIcon())
                                .logo(partner.getLogo()).rails(PresentationUtils.getRails(partner, offer));
                paidPackBuilder.benefits(channelBenefitsBuilder.build());
            }
            abstractPack = paidPackBuilder.build();
        }
        return abstractPack;
    }

    private Set<String> getPackGroupAppIdHierarchySet (String appId){
        Set<String> packGroupAppIdHierarchySet = new HashSet<>();
        for( String productId : cachingService.getProducts().keySet()) {
            ProductDTO product = cachingService.getProducts().get(productId);
            if (!Objects.isNull(product.getAppIdHierarchy()) && !CollectionUtils.isEmpty(product.getAppIdHierarchy())  && product.getAppIdHierarchy().containsKey(appId) && product.getAppIdHierarchy().get(appId) < 0)
                packGroupAppIdHierarchySet.add(product.getPackGroup());
        }
        return packGroupAppIdHierarchySet;
    }
}
