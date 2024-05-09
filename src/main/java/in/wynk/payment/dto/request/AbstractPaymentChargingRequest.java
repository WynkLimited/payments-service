package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.dto.AppStoreDetails;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IPaymentMethodValidatorRequest;
import in.wynk.payment.validations.IProductValidatorRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractPaymentChargingRequest implements IChargingDetails, IPaymentMethodValidatorRequest, IProductValidatorRequest, IClientValidatorRequest, ICouponValidatorRequest {

    @Valid
    @Analysed
    private AbstractProductDetails productDetails;

    @Valid
    @Analysed
    private GeoLocation geoLocation;

    @Analysed
    public AppStoreDetails appStoreDetails;

    @Valid
    @Analysed
    private AbstractPaymentDetails paymentDetails;

    @JsonIgnore
    @Setter
    private String orderId;

    @Override
    public PaymentGateway getPaymentCode () {
        PaymentMethod paymentMethod = BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(getPaymentId());
        return PaymentCodeCachingService.getFromPaymentCode(paymentMethod.getPaymentCode().getCode());
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

    public abstract ClientDetails getClientDetails();

}
