package in.wynk.payment.dto.request;

import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.service.impl.AbstractPreferredPaymentDetailsRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CombinedS2SPaymentDetailsRequest<T extends IProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> {
    private Map<String, List<String>> paymentGroups;

    private IUserDetails userDetails;

    public String getClient() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }

}
