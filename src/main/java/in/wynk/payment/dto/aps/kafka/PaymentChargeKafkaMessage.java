package in.wynk.payment.dto.aps.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.event.PaymentStatusEvent;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.PageUrlDetails;
import in.wynk.payment.dto.UserBillingDetail;
import in.wynk.payment.dto.UserDetails;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;
import java.io.Serializable;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
public class PaymentChargeKafkaMessage extends PaymentKafkaMessage implements Serializable {
    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;
    @Valid
    @Analysed
    private UserBillingDetail.BillingSiDetail billingSiDetail;

    @Valid
    @Analysed
    private PageUrlDetails pageUrlDetails;

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.callback.s2s}") + SLASH + BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(getPaymentDetails().getPaymentId()).getPaymentCode().name();
    }

    @Override
    public boolean isAutoRenewOpted () {
        return this.getPaymentDetails().isAutoRenew();
    }

    @Override
    @JsonIgnore
    public ClientDetails getClientDetails() {
        return (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }

    public IPageUrlDetails getPageUrlDetails() {
        if (Objects.nonNull(pageUrlDetails)) return pageUrlDetails;
        final String successPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.success.page}"), appDetails);
        final String failurePage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.failure.page}"), appDetails);
        final String pendingPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.pending.page}"), appDetails);
        final String unknownPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.unknown.page}"), appDetails);
        return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
    }

    private String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

    public UserDetails getUserDetails() {
        if(getPaymentId().equalsIgnoreCase(ADDTOBILL)){
            return UserBillingDetail.builder().billingSiDetail(billingSiDetail).msisdn(MsisdnUtils.normalizePhoneNumber(userDetails.getMsisdn())).si(userDetails.getSi()).build();
        }
        return userDetails;
    }

    public static PaymentStatusMessage from (PaymentStatusEvent event, PlanDTO planDto) {
        return PaymentStatusMessage.builder().transactionId(event.getId()).status(event.getTransactionStatus()).event(event.getTransactionType()).planId(event.getPlanId()).amount(planDto.getPrice().getDisplayAmount()).discountedAmount(planDto.getPrice().getAmount()).failureReason(
                event.getFailureReason()).build();
    }
}
