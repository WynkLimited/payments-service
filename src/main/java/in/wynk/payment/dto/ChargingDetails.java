package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.IGeoLocation;
import in.wynk.payment.core.dao.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static in.wynk.common.constant.BaseConstants.DEFAULT_COUNTRY_CODE;

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
        return GeoLocation.builder()
                .countryCode(DEFAULT_COUNTRY_CODE)
                .build();
    }
}