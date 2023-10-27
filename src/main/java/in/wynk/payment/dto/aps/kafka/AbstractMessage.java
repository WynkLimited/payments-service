package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractMessage {

    @Analysed
    private AbstractProductDetails productDetails;

    @Analysed
    private GeoLocation geoLocation;

    @Analysed
    private AbstractPaymentDetails paymentDetails;
}
