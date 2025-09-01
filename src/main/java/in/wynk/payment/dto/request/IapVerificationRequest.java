package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.*;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.PageUrlDetails;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PurchaseDetails;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.itune.ItunesVerificationRequest;
import in.wynk.payment.dto.keplerIap.KeplerIapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static in.wynk.common.constant.CacheBeanNameConstants.*;
import static in.wynk.common.constant.CacheBeanNameConstants.OS;


@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode", visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = ItunesVerificationRequest.class, name = "ITUNES"), @JsonSubTypes.Type(value = AmazonIapVerificationRequest.class, name = "AMAZON_IAP"), @JsonSubTypes.Type(value = KeplerIapVerificationRequest.class, name = "KEPLER_IAP")})
public abstract class IapVerificationRequest {


    @Analysed
    @Setter
    private String successUrl;

    @Analysed
    @Setter
    private String failureUrl;

    @Analysed
    private int buildNo;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = OS)
    private String os;

    //    @NotNull TODO Uncomment the following release
    @Analysed
    @MongoBaseEntityConstraint(beanName = APP)
    private String appId;

    @NotBlank
    @Analysed
    private String uid;

    @Setter
    @Analysed
    private String sid;

    @NotNull
    @Analysed
    @Pattern(regexp = MSISDN_REGEX, message = INVALID_VALUE)
    private String msisdn;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = WYNK_SERVICE)
    private String service;

    @NotBlank
    @Analysed
    private String deviceId;

    @Analysed
    private String countryCode;

    @Setter
    private String paymentCode;

    @Analysed
    private GeoLocation geoLocation;


    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }

    private boolean originalSid;

    public void setOriginalSid() {
        this.originalSid = StringUtils.isNotBlank(this.sid);
    }

    public PurchaseDetails getPurchaseDetails() {
        return PurchaseDetails.builder()
                .appDetails(AppDetails.builder().os(getOs()).appId(getAppId()).deviceId(getDeviceId()).buildNo(getBuildNo()).service(getService()).build())
                .userDetails(UserDetails.builder().msisdn(getMsisdn()).countryCode(getCountryCode()).build())
                .geoLocation(getGeoLocation())
                .pageUrlDetails(PageUrlDetails.builder().successPageUrl(getSuccessUrl()).failurePageUrl(getFailureUrl()).build())
                .build();
    }

}