package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;
import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@Builder
@AnalysedEntity
@RequiredArgsConstructor
public class Message implements Serializable {
    private String from;
    private String to;
    private String campaignId;

    @Valid
    @Analysed
    private AbstractProductDetails productDetails;

    @Valid
    @Analysed
    private AbstractPaymentDetails paymentDetails;

    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;

    public boolean isMandateSupported () {
        return this.getPaymentDetails().isMandate();
    }

    public ClientDetails getClientDetails() {
        return (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
