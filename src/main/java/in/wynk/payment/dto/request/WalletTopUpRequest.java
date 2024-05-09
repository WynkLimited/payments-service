package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.phonepe.autodebit.PhonePeAutoDebitTopUpRequest;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IPaymentMethodValidatorRequest;
import in.wynk.payment.validations.IProductValidatorRequest;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode", visible = true, defaultImpl = WalletTopUpRequest.class)
@JsonSubTypes({@JsonSubTypes.Type(value = PhonePeAutoDebitTopUpRequest.class, name = "PHONEPE_AUTO_DEBIT")})
public class WalletTopUpRequest<T extends IPurchaseDetails> implements IPaymentMethodValidatorRequest, IProductValidatorRequest, IClientValidatorRequest, ICouponValidatorRequest {

    @Valid
    @Analysed
    private T purchaseDetails;

    @NotNull
    @Analysed
    private String paymentCode;

    public PaymentGateway getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }

    @Override
    public String getOs() {
        return this.getAppDetails().getOs();
    }

    @Override
    public String getAppId() {
        return this.getAppDetails().getAppId();
    }

    @Override
    public int getBuildNo() {
        return this.getAppDetails().getBuildNo();
    }

    @Override
    public String getMsisdn() {
        return this.getUserDetails().getMsisdn();
    }

    @Override
    public String getService() {
        return this.getAppDetails().getService();
    }

    @Override
    public String getCountryCode() {
        return this.getUserDetails().getCountryCode();
    }

    @Override
    public IAppDetails getAppDetails() {
        return this.purchaseDetails.getAppDetails();
    }

    @Override
    public IUserDetails getUserDetails() {
        return this.purchaseDetails.getUserDetails();
    }

    @Override
    public IProductDetails getProductDetails() {
        return this.purchaseDetails.getProductDetails();
    }

    @Override
    public String getCouponCode() {
        return this.purchaseDetails.getPaymentDetails().getCouponId();
    }

    @Override
    public String getPaymentId() {
        return this.purchaseDetails.getPaymentDetails().getPaymentId();
    }

    @Override
    public boolean isTrialOpted() {
        return this.purchaseDetails.getPaymentDetails().isTrialOpted();
    }

    @Override
    public boolean isAutoRenewOpted() {
        return this.purchaseDetails.getPaymentDetails().isAutoRenew();
    }

    @Override
    public ClientDetails getClientDetails() {
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        return (ClientDetails) (WebPurchaseDetails.class.isAssignableFrom(purchaseDetails.getClass()) ? clientCachingService.getClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT)) : clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()));
    }

    @Override
    public IPaymentDetails getPaymentDetails () {
        return this.purchaseDetails.getPaymentDetails();
    }

}