package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class PageUrlDetails implements IChargingDetails.IPageUrlDetails {
    @Analysed
    private String successPageUrl;
    @Analysed
    private String failurePageUrl;
    @Analysed
    private String pendingPageUrl;
    @Analysed
    private String unknownPageUrl;
}
