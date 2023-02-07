package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IPaymentMethodValidatorRequest;
import in.wynk.payment.validations.IPlanValidatorRequest;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.BaseConstants.CLIENT;

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
    private AppDetails appDetails;
    @Valid
    @Analysed
    private UserDetails userDetails;
    @Analysed
    private PaymentDetails paymentDetails;
    @Analysed
    private ProductDetailsDto productDetails;
    @Analysed
    private GeoLocation geoLocation;
}
