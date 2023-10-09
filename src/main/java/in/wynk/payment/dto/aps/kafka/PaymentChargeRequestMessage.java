package in.wynk.payment.dto.aps.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.event.PaymentStatusEvent;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.validations.IPaymentMethodValidatorRequest;
import in.wynk.payment.validations.IPlanValidatorRequest;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@AnalysedEntity
@NoArgsConstructor
public class PaymentChargeRequestMessage implements IPaymentMethodValidatorRequest, IPlanValidatorRequest, IClientValidatorRequest {
    private String from;
    private String to;
    private String campaignId;
    private String orgId;
    private String sessionId;
    private String serviceId;
    private String requestId;

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

    @Override
    @JsonIgnore
    public ClientDetails getClientDetails() {
        return (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }

    @Override
    public boolean isAutoRenewOpted () {
        return this.getPaymentDetails().isAutoRenew();
    }

    @Override
    public int getBuildNo () {
        return this.getAppDetails().getBuildNo();
    }

    @Override
    public String getOs () {
        return this.getAppDetails().getOs();
    }

    @Override
    public String getAppId () {
        return this.getAppDetails().getAppId();
    }

    @Override
    public String getMsisdn () {
        return this.getUserDetails().getMsisdn();
    }

    @Override
    public String getService () {
        return this.getAppDetails().getService();
    }

    @Override
    public String getPaymentId () {
        return this.getPaymentDetails().getPaymentId();
    }

    @Override
    public boolean isTrialOpted () {
        return this.getPaymentDetails().isTrialOpted();
    }

    public boolean isMandateSupported () {
        return this.getPaymentDetails().isMandate();
    }
}
