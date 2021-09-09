package in.wynk.payment.dto.request;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.MsisdnUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SWalletValidateLinkRequest extends WalletValidateLinkRequest {
    @Analysed
    private String deviceId;
    @Analysed
    private String msisdn;

    @Override
    @JsonIgnore
    public String getUid() {
        return MsisdnUtils.getUidFromMsisdn(msisdn);
    }

    @Override
    @JsonIgnore
    public String getClient() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }
}
