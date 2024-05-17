package in.wynk.payment.presentation;

import com.google.common.base.Strings;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.common.response.PaymentStatusWrapper;
import in.wynk.payment.dto.response.AbstractChargingStatusResponseV2;
import in.wynk.payment.dto.response.ChargingStatusResponseV2;
import in.wynk.payment.dto.response.ChargingStatusResponseV3;
import in.wynk.payment.dto.response.FailureChargingStatusResponseV2;
import in.wynk.payment.dto.response.presentation.paymentstatus.AbstractAirtelPaymentStatus;
import in.wynk.payment.dto.response.presentation.paymentstatus.AbstractPayUPaymentStatus;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PresentationUtils;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;

/**
 * @author Nishesh Pandey
 */

@Component
@RequiredArgsConstructor
public class PaymentStatusPresentation implements IPaymentPresentation<AbstractChargingStatusResponseV2, PaymentStatusWrapper> {


    private final PaymentCachingService cachingService;
    private final PaymentMethodCachingService paymentMethodCachingService;


    /*protected PaymentStatusPresentation (PaymentCachingService cachingService) {
        this.cachingService = cachingService;
    }*/

    private final Map<String, IPaymentPresentation<? extends AbstractChargingStatusResponseV2, PaymentStatusWrapper>> delegate = new HashMap<>();

    @PostConstruct
    public void init() {
        delegate.put("payu", new PayUPaymentStatus());
        delegate.put("aps", new APSPaymentStatus());

    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponseV2> transform (PaymentStatusWrapper payload) throws URISyntaxException {
        final PaymentMethod method = paymentMethodCachingService.get(payload.getPaymentId());
        return (WynkResponseEntity<AbstractChargingStatusResponseV2>) delegate.get(method.getGroup()).transform(payload);
    }

    private AbstractPack getPackDetails (Transaction transaction, int planId) {
        final PlanDTO plan = cachingService.getPlan(planId);
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        final PartnerDTO partner = cachingService.getPartner(Optional.ofNullable(offer.getPackGroup()).orElse(BaseConstants.DEFAULT_PACK_GROUP + offer.getService()));
        AbstractPack abstractPack = null;
        String appId = null;
        final Optional<IPurchaseDetails> purchaseDetails = TransactionContext.getPurchaseDetails();
        if (purchaseDetails.isPresent() && purchaseDetails.get().getAppDetails() != null) {
            appId = purchaseDetails.get().getAppDetails().getAppId();
        }
        if (transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            abstractPack = getAbstractPackForTrialSubscription(transaction, offer, plan, partner, appId);
        } else {
            PaidPack.PaidPackBuilder<?, ?> paidPackBuilder =
                    PaidPack.builder().title(offer.getTitle()).amount(plan.getFinalPrice()).period(plan.getPeriod().getValidity()).timeUnit(plan.getPeriod().getTimeUnit().name())
                            .month(plan.getPeriod().getMonth()).perMonthValue(plan.getPrice().getMonthlyAmount().intValue()).dailyAmount(plan.getPrice().getDailyAmount()).day(plan.getPeriod().getDay())
                            .currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
            if (offer.isCombo()) {
                final BundleBenefits.BundleBenefitsBuilder<?, ?> benefitsBuilder =
                        BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner, offer));
                final List<ChannelBenefits> channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner)
                        .map(channelPartner -> ChannelBenefits.builder().packGroup(channelPartner.getPackGroup()).name(channelPartner.getName()).notVisible(channelPartner.isNotVisible())
                                .icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(
                                Collectors.toList());

                if (!Strings.isNullOrEmpty(appId)) {
                    Set<String> packgroupAppIdHierarchySet = getPackGroupAppIdHierarchySet(appId.toUpperCase());
                    channelBenefits.stream().forEach(channelBenefit -> {
                        if (!channelBenefit.isNotVisible() && channelBenefit.getPackGroup() != null && packgroupAppIdHierarchySet.contains(channelBenefit.getPackGroup())) {
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

    private AbstractPack getAbstractPackForTrialSubscription (Transaction transaction, OfferDTO offer, PlanDTO plan, PartnerDTO partner, String appId) {
        final PlanDTO paidPlan = cachingService.getPlan(transaction.getPlanId());
        final TrialPack.TrialPackBuilder<?, ?> trialPackBuilder =
                TrialPack.builder().title(offer.getTitle()).day(plan.getPeriod().getDay()).amount(plan.getFinalPrice()).month(plan.getPeriod().getMonth()).period(plan.getPeriod().getValidity())
                        .timeUnit(plan.getPeriod().getTimeUnit().name()).currency(plan.getPrice().getCurrency()).isCombo(offer.isCombo());
        if (offer.isCombo()) {
            final BundleBenefits.BundleBenefitsBuilder<?, ?> bundleBenefitsBuilder =
                    BundleBenefits.builder().id(partner.getId()).name(partner.getName()).icon(partner.getIcon()).logo(partner.getLogo()).rails(PresentationUtils.getRails(partner, offer));
            final List<ChannelBenefits>
                    channelBenefits = offer.getProducts().values().stream().map(cachingService::getPartner)
                    .map(channelPartner -> ChannelBenefits.builder().name(channelPartner.getName()).notVisible(channelPartner.isNotVisible()).packGroup(channelPartner.getPackGroup())
                            .icon(channelPartner.getIcon()).logo(channelPartner.getLogo()).build()).collect(Collectors.toList());
            if (!Strings.isNullOrEmpty(appId)) {
                Set<String> packGroupAppIdHierarchySet = getPackGroupAppIdHierarchySet(appId.toUpperCase());
                channelBenefits.stream().forEach(channelBenefit -> {
                    if (!channelBenefit.isNotVisible() && channelBenefit.getPackGroup() != null && packGroupAppIdHierarchySet.contains(channelBenefit.getPackGroup())) {
                        channelBenefit.setNotVisible(true);
                    }
                });
            }
            trialPackBuilder.benefits(bundleBenefitsBuilder.channelsBenefits(channelBenefits).build());

        } else {
            final ChannelBenefits.ChannelBenefitsBuilder<?, ?> channelBenefitsBuilder =
                    ChannelBenefits.builder().id(partner.getId()).name(partner.getName()).notVisible(partner.isNotVisible()).packGroup(partner.getPackGroup()).icon(partner.getIcon())
                            .logo(partner.getLogo()).rails(
                                    PresentationUtils.getRails(partner, offer));
            trialPackBuilder.benefits(channelBenefitsBuilder.build());
        }
        return trialPackBuilder.paidPack(
                PaidPack.builder().title(paidPlan.getTitle()).amount(paidPlan.getFinalPrice()).period(paidPlan.getPeriod().getValidity()).timeUnit(paidPlan.getPeriod().getTimeUnit().name())
                        .month(paidPlan.getPeriod().getMonth()).perMonthValue(paidPlan.getPrice().getMonthlyAmount().intValue()).day(paidPlan.getPeriod().getDay())
                        .dailyAmount(paidPlan.getPrice().getDailyAmount()).currency(paidPlan.getPrice().getCurrency()).build()).isCombo(offer.isCombo()).build();
    }

    private Set<String> getPackGroupAppIdHierarchySet (String appId) {
        Set<String> packGroupAppIdHierarchySet = new HashSet<>();
        for (String productId : cachingService.getProducts().keySet()) {
            ProductDTO product = cachingService.getProducts().get(productId);
            if (!Objects.isNull(product.getAppIdHierarchy()) && !CollectionUtils.isEmpty(product.getAppIdHierarchy()) && product.getAppIdHierarchy().containsKey(appId) &&
                    product.getAppIdHierarchy().get(appId) < 0) {
                packGroupAppIdHierarchySet.add(product.getPackGroup());
            }
        }
        return packGroupAppIdHierarchySet;
    }

    private class PayUPaymentStatus implements IPaymentPresentation<AbstractPayUPaymentStatus, PaymentStatusWrapper> {

        @SneakyThrows
        @Override
        public WynkResponseEntity<AbstractPayUPaymentStatus> transform (PaymentStatusWrapper payload) {
            final Transaction transaction = payload.getTransaction();
            final IChargingDetails.IPageUrlDetails pageUrlDetails =
                    TransactionContext.getPurchaseDetails().map(details -> (IChargingDetails) details).map(IChargingDetails::getPageUrlDetails).orElseGet(() -> {
                        // NOTE: Added backward support to avoid failure for transaction created pre payment refactoring build, once the build is live it has no significance
                        final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(transaction.getClientAlias());
                        final String service = cachingService.getPlan(transaction.getPlanId()).getService();
                        return PresentationUtils.getPageDetails(service, clientAlias);
                    });
            final TransactionStatus txnStatus = transaction.getStatus();
            if (EnumSet.of(TransactionStatus.FAILURE, TransactionStatus.FAILUREALREADYSUBSCRIBED).contains(txnStatus)) {
                return failure(payload.getErrorCode(), transaction, payload.getPlanId(), pageUrlDetails.getFailurePageUrl());
            } else if (txnStatus == TransactionStatus.INPROGRESS) {
               return failure(payload.getErrorCode(), transaction, payload.getPlanId(), pageUrlDetails.getPendingPageUrl());
            } else {
                ChargingStatusResponseV2.ChargingStatusResponseV2Builder<?, ?> builder =
                        ChargingStatusResponseV2.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).planId(payload.getPlanId())
                                .validity(cachingService.validTillDate(payload.getPlanId(),payload.getTransaction().getMsisdn()));
                if (txnStatus == TransactionStatus.SUCCESS) {
                    builder.packDetails(getPackDetails(transaction, payload.getPlanId()));
                    builder.redirectUrl(pageUrlDetails.getSuccessPageUrl());
                }
                return WynkResponseEntity.<AbstractPayUPaymentStatus>builder().data(builder.build()).build();
            }
        }
    }

    private WynkResponseEntity<AbstractPayUPaymentStatus> failure (ErrorCode errorCode, Transaction transaction, int planId, String redirectUrl) {
        final Optional<String> subtitle = errorCode.getMeta(SUBTITLE_TEXT);
        final Optional<String> buttonText = errorCode.getMeta(BUTTON_TEXT);
        final Optional<Boolean> buttonArrow = errorCode.getMeta(BUTTON_ARROW);
        final FailureChargingStatusResponseV2
                failureChargingStatusResponse =
                FailureChargingStatusResponseV2.populate(errorCode, subtitle.orElse(""), buttonText.orElse(""), buttonArrow.orElse(Boolean.FALSE), transaction.getIdStr(), planId,
                        getPackDetails(transaction, planId), transaction.getStatus(), redirectUrl);
        return WynkResponseEntity.<AbstractPayUPaymentStatus>builder().data(failureChargingStatusResponse).build();
    }

    private class APSPaymentStatus implements IPaymentPresentation<AbstractAirtelPaymentStatus, PaymentStatusWrapper> {

        @SneakyThrows
        @Override
        public WynkResponseEntity<AbstractAirtelPaymentStatus> transform (PaymentStatusWrapper payload) {
            final Transaction transaction = payload.getTransaction();
            if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                return WynkResponseEntity.<AbstractAirtelPaymentStatus>builder().data(ChargingStatusResponseV3.success(transaction.getIdStr(), cachingService.validTillDate(transaction.getPlanId(),payload.getTransaction().getMsisdn()), transaction.getPlanId())).build();
            } else if (transaction.getStatus() == TransactionStatus.FAILURE) {
                return WynkResponseEntity.<AbstractAirtelPaymentStatus>builder().data(ChargingStatusResponseV3.failure(transaction.getIdStr(), transaction.getPlanId())).build();
            }
            throw new WynkRuntimeException(PaymentErrorType.APS006);
            }
        }
}
