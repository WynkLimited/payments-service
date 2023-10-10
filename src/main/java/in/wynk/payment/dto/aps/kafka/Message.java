package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;
import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@AnalysedEntity
@RequiredArgsConstructor
public class Message extends AbstractMessage implements Serializable {
    private String from;
    private String to;
    private String campaignId;
    private String state;
    private String type;
    private String channel;

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
