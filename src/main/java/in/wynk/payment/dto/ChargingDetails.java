package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.IGeoLocation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.GEO_LOCATION;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class ChargingDetails implements IChargingDetails {

    @Analysed
    private String callbackUrl;
    @Analysed
    private IAppDetails appDetails;
    @Analysed
    private IUserDetails userDetails;
    @Analysed
    private IPaymentDetails paymentDetails;
    @Analysed
    private IProductDetails productDetails;
    private IPageUrlDetails pageUrlDetails;

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> callbackUrl;
    }


    @Override
    public IGeoLocation getGeoLocation() {
        SessionDTO session = SessionContextHolder.getBody();
        GeoLocation geoLocation = session.get(GEO_LOCATION);
        return Objects.isNull(geoLocation) ? GeoLocation.builder().build() :
                GeoLocation.builder().accessCountryCode(geoLocation.getAccessCountryCode()).stateCode(geoLocation.getStateCode()).ip(geoLocation.getIp()).build();
    }

    @Override
    public ISessionDetails getSessionDetails () {
        return null;
    }
}