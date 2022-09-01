package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.SessionRequest;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.subscription.common.dto.GeoLocation;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.api.validations.IWynkServiceValidatorRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.util.Optional;

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

    @Analysed
    private GeoLocation geoLocation;

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
        final Optional<IChargingDetails.IPageUrlDetails> pageUrlDetailsOption = Optional.ofNullable(getPageUrlDetails());
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
                .failureUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getFailurePageUrl).orElse(null))
                .successUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getSuccessPageUrl).orElse(null))
                .pendingUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getPendingPageUrl).orElse(null))
                .unknownUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getUnknownPageUrl).orElse(null))
                .build();
    }

}
