package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.payment.core.dao.entity.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractChargingRequestV2 {

    @Valid
    @Analysed
    private IAppDetails appDetails;
    @Valid
    @Analysed
    private IUserDetails userDetails;
    @Analysed
    private IPaymentDetails paymentDetails;
    @Analysed
    private IProductDetails productDetails;
    @Analysed
    private GeoLocation geoLocation;
}
