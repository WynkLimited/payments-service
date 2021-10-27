package in.wynk.payment.dto.request;

import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CombinedS2SPaymentDetailsRequest extends AbstractPreferredPaymentDetailsControllerRequest {

    private String uid;
    private String deviceId;
    private Map<String, List<String>> paymentGroups;

    public String getClient() {
        return BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }

}