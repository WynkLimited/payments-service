package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.MiscellaneousDetails;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.constant.Validity;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails.IPageUrlDetails;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.wynkservice.api.validations.IWynkServiceValidatorRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.util.Map;
import java.util.Optional;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BestValuePlanPurchaseRequest implements IClientValidatorRequest, IWynkServiceValidatorRequest {

    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;

    @Valid
    @Analysed
    private AbstractBestValueProductDetails productDetails;

    @Analysed
    private PageUrlDetails pageUrlDetails;

    @Analysed
    private MiscellaneousDetails miscellaneousDetails;

    @Analysed
    private GeoLocation geoLocation;

    @Override
    public String getOs () {
        return appDetails.getOs();
    }

    @Override
    public String getAppId () {
        return appDetails.getAppId();
    }

    @Override
    public String getService () {
        return appDetails.getService();
    }

    public SessionRequest toSession () {
        final Optional<IPageUrlDetails> pageUrlDetailsOption = Optional.ofNullable(getPageUrlDetails());
        SessionRequest.SessionRequestBuilder sessionRequestBuilder = SessionRequest.builder()
                .appId(getAppDetails().getAppId())
                .appVersion(getAppDetails().getAppVersion())
                .buildNo(getAppDetails().getBuildNo())
                .deviceId(getAppDetails().getDeviceId())
                .deviceType(getAppDetails().getDeviceType())
                .os(getAppDetails().getOs())
                .geoLocation(getGeoLocation())
                .service(getAppDetails().getService())
                .countryCode(getUserDetails().getCountryCode())
                .msisdn(getUserDetails().getMsisdn())
                .miscellaneousDetails(getMiscellaneousDetails())
                .uid(IdentityUtils.getUidFromUserName(getUserDetails().getMsisdn(), getAppDetails().getService()))
                .failureUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getFailurePageUrl).orElse(null))
                .successUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getSuccessPageUrl).orElse(null))
                .pendingUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getPendingPageUrl).orElse(null))
                .unknownUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getUnknownPageUrl).orElse(null));
        return sessionRequestBuilder.build();
    }

    public SessionRequest toSessionWithAdditionalParam (Map<String, String> additionalPram) {
        final Optional<IPageUrlDetails> pageUrlDetailsOption = Optional.ofNullable(getPageUrlDetails());
        return SessionRequest.builder()
                .appId(getAppDetails().getAppId())
                .appVersion(getAppDetails().getAppVersion())
                .buildNo(getAppDetails().getBuildNo())
                .deviceId(getAppDetails().getDeviceId())
                .deviceType(getAppDetails().getDeviceType())
                .os(getAppDetails().getOs())
                .geoLocation(getGeoLocation())
                .service(getAppDetails().getService())
                .countryCode(getUserDetails().getCountryCode())
                .msisdn(getUserDetails().getMsisdn())
                .miscellaneousDetails(getMiscellaneousDetails())
                .uid(IdentityUtils.getUidFromUserName(getUserDetails().getMsisdn(), getAppDetails().getService()))
                .failureUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getFailurePageUrl).orElse(null))
                .successUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getSuccessPageUrl).orElse(null))
                .pendingUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getPendingPageUrl).orElse(null))
                .unknownUrl(pageUrlDetailsOption.map(IChargingDetails.IPageUrlDetails::getUnknownPageUrl).orElse(null))
                .packGroup(additionalPram.get(BaseConstants.DEEPLINK_PACK_GROUP))
                .validity(Validity.getValidity(additionalPram.get(BaseConstants.VALIDITY)))
                .validityUnit(additionalPram.get(BaseConstants.VALIDITY))
                .price(Optional.ofNullable(additionalPram.get(BaseConstants.PRICE)).map(Double::parseDouble).orElse(null))
                .build();
    }
}
