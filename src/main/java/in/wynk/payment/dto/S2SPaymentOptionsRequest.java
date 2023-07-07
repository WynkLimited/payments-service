package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.utils.BeanLocatorFactory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SPaymentOptionsRequest implements IPaymentOptionsRequest {

    @Analysed
    private String couponId;
    @Analysed
    private AppDetails appDetails;
    @Analysed
    private UserDetails userDetails;
    @Analysed
    private AbstractProductDetails productDetails;
    @Analysed
    private GeoLocation geoLocation;

    @Override
    @JsonIgnore
    public String getClient() {
        return BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }
}