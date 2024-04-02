package in.wynk.payment.dto.request;

import in.wynk.client.validations.IClientValidatorRequest;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.validations.ICouponValidatorRequest;
import in.wynk.payment.validations.IProductValidatorRequest;
import in.wynk.payment.validations.IReceiptValidatorRequest;
import org.apache.commons.lang.StringUtils;

import java.util.Objects;

public class IapVerificationWrapperRequest implements IProductValidatorRequest, IClientValidatorRequest, ICouponValidatorRequest, IReceiptValidatorRequest<LatestReceiptResponse> {

    private final LatestReceiptResponse latestReceiptResponse;
    private final IapVerificationRequest iapVerificationRequest;
    private final IapVerificationRequestV2 iapVerificationRequestV2;

    public IapVerificationWrapperRequest(LatestReceiptResponse latestReceiptResponse, IapVerificationRequest iapVerificationRequest, IapVerificationRequestV2 iapVerificationRequestV2) {
        this.latestReceiptResponse = latestReceiptResponse;
        this.iapVerificationRequest = iapVerificationRequest;
        this.iapVerificationRequestV2 = iapVerificationRequestV2;
    }

    @Override
    public String getMsisdn() {
        if(iapVerificationRequest != null){
            return this.iapVerificationRequest.getMsisdn();
        }
        return this.iapVerificationRequestV2.getUserDetails().getMsisdn();
    }

    @Override
    public String getService() {
        if(iapVerificationRequest != null) {
            return this.iapVerificationRequest.getService();
        }
        return this.iapVerificationRequestV2.getAppDetails().getService();
    }

    @Override
    public String getCouponCode() {
        return this.latestReceiptResponse.getCouponCode();
    }

    @Override
    public PaymentGateway getPaymentCode() {
        if(iapVerificationRequest != null) {
            return this.iapVerificationRequest.getPaymentGateway();
        }
        return this.iapVerificationRequestV2.getPaymentCode();
    }

    @Override
    public boolean isTrialOpted() {
        return this.latestReceiptResponse.isFreeTrial();
    }

    @Override
    public boolean isAutoRenewOpted() {
        return Boolean.TRUE;
    }

    @Override
    public IAppDetails getAppDetails() {
        return AppDetails.builder()
                .appId(StringUtils.isNotBlank(this.iapVerificationRequest.getAppId()) ? this.iapVerificationRequest.getAppId() : (this.iapVerificationRequest.getPaymentGateway().getId().equalsIgnoreCase("ITUNES") ? "MOBILITY" : "FIRESTICK"))
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


    @Override
    public LatestReceiptResponse getLatestReceiptInfo() {
        return latestReceiptResponse;
    }

    @Override
    public IPaymentDetails getPaymentDetails () {
        if(Objects.nonNull(iapVerificationRequest)){
            return this.iapVerificationRequest.getPurchaseDetails().getPaymentDetails();
        } else if(Objects.nonNull(iapVerificationRequestV2)){
            return this.iapVerificationRequestV2.getPurchaseDetails().getPaymentDetails();
        }
        return null;
    }
}