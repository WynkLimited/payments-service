package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.IMiscellaneousDetails;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SPaymentOptionsRequest implements IPaymentOptionsRequest {

    @Analysed
    private String couponId;
    @Analysed
    private boolean mandate;
    @Analysed
    private AppDetails appDetails;
    @Analysed
    private UserDetails userDetails;

    @Analysed
    private GeoLocation geoLocation;

    @Analysed
    private AbstractProductDetails productDetails;
    @Analysed
    private AbstractPaymentDetails paymentDetails;

    @Override
    public IMiscellaneousDetails getMiscellaneousDetails() {
        return null;
    }



    @Override
    @JsonIgnore
    public String getClient() {
        return BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }

    @Override
    public IPaymentDetails getPaymentDetails () {
        return this.paymentDetails;
    }
}