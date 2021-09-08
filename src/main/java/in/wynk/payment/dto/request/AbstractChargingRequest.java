package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.payu.PayUChargingRequest;
import in.wynk.payment.dto.response.phonepe.auto.PhonePeChargingRequest;
import in.wynk.payment.validations.IClientValidatorRequest;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IPaymentMethodValidatorRequest;
import in.wynk.payment.validations.IPlanValidatorRequest;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode", visible = true, defaultImpl = DefaultChargingRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PayUChargingRequest.class, name = "PAYU"),
        @JsonSubTypes.Type(value = PhonePeChargingRequest.class, name = "PHONEPE_AUTO_DEBIT")
})
public abstract class AbstractChargingRequest<T extends IPurchaseDetails> implements IPaymentMethodValidatorRequest, IPlanValidatorRequest, IClientValidatorRequest, ICouponValidatorRequest {

    @Analysed
    private T purchaseDetails;

    @Analysed
    private PaymentCode paymentCode;

    @Override
    public IProductDetails getProductDetails() {
        return this.purchaseDetails.getProductDetails();
    }

    @Override
    public String getMsisdn() {
        return this.purchaseDetails.getUserDetails().getMsisdn();
    }

    @Override
    public String getService() {
        return this.purchaseDetails.getAppDetails().getService();
    }

    @Override
    public String getCouponCode() {
        return this.purchaseDetails.getPaymentDetails().getCouponId();
    }

    @Override
    public ClientDetails getClientDetails() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return (ClientDetails) (WebPurchaseDetails.class.isAssignableFrom(purchaseDetails.getClass()) ? clientCachingService.getClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT)) : clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()));
    }

}