package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SPurchaseDetails implements IChargingDetails {
    @Analysed
    private AppDetails appDetails;
    @Analysed
    private UserDetails userDetails;
    @Analysed
    private PaymentDetails paymentDetails;
    @Analysed
    private PageUrlDetails pageUrlDetails;
    @Analysed
    private AbstractProductDetails productDetails;

    public IPageUrlDetails getPageUrlDetails() {
        if(Objects.nonNull(pageUrlDetails)) return pageUrlDetails;
        final String successPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.success.page}"), appDetails);
        final String failurePage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.failure.page}"), appDetails);
        final String pendingPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.pending.page}"), appDetails);
        final String unknownPage = buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.unknown.page}"), appDetails);
        return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
    }
}
