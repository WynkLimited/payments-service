package in.wynk.payment.dto.request;

import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IPlanValidatorRequest;
import org.apache.commons.lang.StringUtils;

public class IapVerificationWrapperRequest implements IPlanValidatorRequest, IClientValidatorRequest, ICouponValidatorRequest {

    private final LatestReceiptResponse latestReceiptResponse;
    private final IapVerificationRequest iapVerificationRequest;

    public IapVerificationWrapperRequest(LatestReceiptResponse latestReceiptResponse, IapVerificationRequest iapVerificationRequest) {
        this.latestReceiptResponse = latestReceiptResponse;
        this.iapVerificationRequest = iapVerificationRequest;
    }

    @Override
    public String getMsisdn() {
        return this.iapVerificationRequest.getMsisdn();
    }

    @Override
    public String getService() {
        return this.iapVerificationRequest.getService();
    }

    @Override
    public String getCouponCode() {
        return this.latestReceiptResponse.getCouponCode();
    }

    @Override
    public PaymentCode getPaymentCode() {
        return this.iapVerificationRequest.getPaymentCode();
    }

    @Override
    public boolean isTrialOpted() {
        return this.latestReceiptResponse.isFreeTrial();
    }

    @Override
    public IAppDetails getAppDetails() {
        return AppDetails.builder()
                .appId(StringUtils.isNotBlank(this.iapVerificationRequest.getAppId()) ? this.iapVerificationRequest.getAppId() : (this.iapVerificationRequest.getPaymentCode().getId().equalsIgnoreCase("ITUNES") ? "MOBILITY" : "FIRESTICK"))
                .deviceId(this.iapVerificationRequest.getDeviceId())
                .buildNo(this.iapVerificationRequest.getBuildNo())
                .service(this.iapVerificationRequest.getService())
                .os(this.iapVerificationRequest.getOs())
                .build();
    }

    @Override
    public IUserDetails getUserDetails() {
        return UserDetails.builder().msisdn(this.iapVerificationRequest.getMsisdn()).countryCode(this.iapVerificationRequest.getCountryCode()).build();
    }

    @Override
    public IProductDetails getProductDetails() {
        return PlanDetails.builder().planId(this.latestReceiptResponse.getPlanId()).build();
    }

}