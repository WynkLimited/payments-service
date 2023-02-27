package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class AbstractChargingRequestV2 {

    //@Valid
    @Analysed
    private AppDetails appDetails;

  //  @Valid
    @Analysed
    private UserDetails userDetails;

    @Analysed
    private AbstractProductDetails productDetails;

    @Analysed
    private GeoLocation geoLocation;

    private AbstractPaymentDetails paymentDetails;

}
