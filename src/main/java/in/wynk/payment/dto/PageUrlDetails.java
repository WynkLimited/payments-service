package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IChargingDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageUrlDetails implements IChargingDetails.IPageUrlDetails {
    private String successPageUrl;
    private String failurePageUrl;
    private String pendingPageUrl;
    private String unknownPageUrl;
    private String fallBackPageUrl;
}
