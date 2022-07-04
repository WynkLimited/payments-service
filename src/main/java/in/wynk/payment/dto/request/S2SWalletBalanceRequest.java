package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.identity.client.utils.IdentityUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static in.wynk.common.constant.CacheBeanNameConstants.*;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SWalletBalanceRequest extends WalletBalanceRequest {

    @NotNull
    @Analysed
    @Pattern(regexp = MSISDN_REGEX, message = INVALID_VALUE)
    private String msisdn;

    @NotBlank
    @Analysed
    @MongoBaseEntityConstraint(beanName = WYNK_SERVICE)
    private String service;

    @NotBlank
    @Analysed
    private String deviceId;

    @Override
    @JsonIgnore
    public String getUid() {
        return IdentityUtils.getUidFromUserName(this.msisdn, this.service);
    }

    @Override
    @JsonIgnore
    public String getClient() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).getAlias();
    }

}