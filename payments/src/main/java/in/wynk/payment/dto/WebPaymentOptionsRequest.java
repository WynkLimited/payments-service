package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.common.dto.*;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WebPaymentOptionsRequest implements IPaymentOptionsRequest {

    private String couponId;
    private boolean mandate;
    private AbstractProductDetails productDetails;
    private AbstractPaymentDetails paymentDetails;

    @Analysed
    private GeoLocation geoLocation;

    @Override
    @Analysed
    @JsonIgnore
    public IAppDetails getAppDetails() {
        SessionDTO session = SessionContextHolder.getBody();
        return AppDetails.builder().deviceType(session.get(DEVICE_TYPE)).deviceId(session.get(DEVICE_ID)).buildNo(session.get(BUILD_NO)).service(session.get(SERVICE)).appId(session.get(APP_ID)).appVersion(session.get(APP_VERSION)).os(session.get(OS)).build();
    }

    @Override
    @Analysed
    @JsonIgnore
    public IUserDetails getUserDetails() {
        final SessionDTO session = SessionContextHolder.getBody();
        return UserDetails.builder().msisdn(MsisdnUtils.normalizePhoneNumber(session.get(MSISDN))).dslId(session.get(DSL_ID)).subscriberId(session.get(SUBSCRIBER_ID)).countryCode(session.get(COUNTRY_CODE)).si(session.get(SI)).build();
    }

    @Override
    @JsonIgnore
    public String getClient() {
        return ClientContext.getClient().map(Client::getAlias).orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
    }

    @Override
    public IGeoLocation getGeoLocation() {
        SessionDTO session = SessionContextHolder.getBody();
        GeoLocation geoLocation = session.get(GEO_LOCATION);
        return Objects.isNull(geoLocation) ? GeoLocation.builder().build() :
                GeoLocation.builder().accessCountryCode(geoLocation.getAccessCountryCode()).stateCode(geoLocation.getStateCode()).ip(geoLocation.getIp()).build();
    }

    @Override
    public IPaymentDetails getPaymentDetails () {
        return this.paymentDetails;
    }

    @Override
    public IMiscellaneousDetails getMiscellaneousDetails() {
        SessionDTO sessionDTO= SessionContextHolder.getBody();
        MiscellaneousDetails miscellaneousDetails= sessionDTO.get(MISCELLANEOUS_DETAILS);
        return Objects.isNull(miscellaneousDetails) ? MiscellaneousDetails.builder().build() :
                MiscellaneousDetails.builder().ingressIntent(miscellaneousDetails.getIngressIntent()).autoRenew(miscellaneousDetails.isAutoRenew()).mandate(miscellaneousDetails.isMandate()).trialOpted(miscellaneousDetails.isTrialOpted()).build();
    }

}