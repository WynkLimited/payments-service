package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.PageUrlDetails;
import in.wynk.payment.dto.UserBillingDetail;
import in.wynk.payment.dto.UserDetails;
import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;

import static in.wynk.common.constant.BaseConstants.SLASH;

@Getter
public class S2SChargingRequestV2 extends AbstractChargingRequestV2 {

    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;
    @Valid
    @Analysed
    private UserBillingDetail.BillingSiDetail billingSiDetail;

    @Valid
    @Analysed
    private PageUrlDetails pageUrlDetails;

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.callback.s2s}") + SLASH + BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(getPaymentDetails().getPaymentId()).getPaymentCode().name();
    }

    @Override
    public boolean isAutoRenewOpted () {
        return this.getPaymentDetails().isAutoRenew();
    }
}
