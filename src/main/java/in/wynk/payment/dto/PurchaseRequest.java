package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.SessionRequest;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.api.validations.IWynkServiceValidatorRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseRequest implements IClientValidatorRequest, IWynkServiceValidatorRequest {

    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;

    @Valid
    @Analysed
    private AbstractProductDetails productDetails;

    @Analysed
    private PageUrlDetails pageUrlDetails;

    public IChargingDetails.IPageUrlDetails getPageUrlDetails() {
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

    @Override
    public String getOs() {
        return appDetails.getOs();
    }

    @Override
    public String getAppId() {
        return appDetails.getAppId();
    }

    @Override
    public String getService() {
        return appDetails.getService();
    }

    public SessionRequest toSession() {
        final IChargingDetails.IPageUrlDetails pageUrlDetails = getPageUrlDetails();
        return SessionRequest.builder()
                .appId(getAppDetails().getAppId())
                .appVersion(getAppDetails().getAppVersion())
                .buildNo(getAppDetails().getBuildNo())
                .deviceId(getAppDetails().getDeviceId())
                .deviceType(getAppDetails().getDeviceType())
                .os(getAppDetails().getOs())
                .service(getAppDetails().getService())
                .countryCode(getUserDetails().getCountryCode())
                .msisdn(getUserDetails().getMsisdn())
                .uid(MsisdnUtils.getUidFromMsisdn(getUserDetails().getMsisdn(), WynkServiceUtils.fromServiceId(getAppDetails().getService()).getSalt()))
                .failureUrl(pageUrlDetails.getFailurePageUrl())
                .successUrl(pageUrlDetails.getSuccessPageUrl())
                .build();
    }

}
