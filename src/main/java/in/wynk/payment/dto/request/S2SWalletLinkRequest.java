package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.validation.constraints.NotBlank;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SWalletLinkRequest extends WalletLinkRequest {
    @NotBlank
    @Analysed
    private String deviceId;

    @Override
    public String getClient() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }

}

