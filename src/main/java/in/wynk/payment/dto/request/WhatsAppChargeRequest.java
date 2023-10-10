package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.PageUrlDetails;
import in.wynk.payment.dto.UserBillingDetail;
import in.wynk.payment.dto.UserDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;

/**
 * @author Nishesh Pandey
 */

@Getter
@Setter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppChargeRequest extends S2SChargingRequestV2 {
    private String clientAlias;
    @Override
    @JsonIgnore
    public ClientDetails getClientDetails() {
        return (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientByAlias(this.clientAlias);
    }


}
