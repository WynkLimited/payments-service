package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IPaymentMethodValidatorRequest;
import in.wynk.payment.validations.IPlanValidatorRequest;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;

import static in.wynk.common.constant.BaseConstants.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractChargingRequestV2 implements IChargingDetails, IPaymentMethodValidatorRequest, IPlanValidatorRequest, IClientValidatorRequest, ICouponValidatorRequest {

    @Valid
    @Analysed
    private AbstractProductDetails productDetails;

    @Valid
    @Analysed
    private GeoLocation geoLocation;

    @Valid
    @Analysed
    private AbstractPaymentDetails paymentDetails;

    @Override
    public PaymentGateway getPaymentCode () {
        return PaymentCodeCachingService.getFromPaymentCode(getPaymentId());
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
    public String getCouponCode () {
        return this.getPaymentDetails().getCouponId();
    }

    @Override
    public String getCountryCode () {
        if(this.getGeoLocation() == null){
            return "IN";
        }
        return this.getGeoLocation().getCountryCode();
    }

    @Override
    public boolean isTrialOpted () {
        return this.getPaymentDetails().isTrialOpted();
    }

}
