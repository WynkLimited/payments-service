package in.wynk.payment.dto.request;

import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CombinedS2SPaymentDetailsRequest extends AbstractPreferredPaymentDetailsControllerRequest {

    private String uid;
    private String deviceId;

    private String si;

    public String getClient() {
        return BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }

}

