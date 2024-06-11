package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.MiscellaneousDetails;
import in.wynk.wynkservice.api.validations.IWynkServiceValidatorRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;

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
}
