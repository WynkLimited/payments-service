package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.*;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
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
