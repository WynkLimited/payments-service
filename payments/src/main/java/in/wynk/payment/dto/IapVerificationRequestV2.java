package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.*;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PurchaseDetails;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.itune.ItunesVerificationRequest;
import in.wynk.payment.dto.keplerIap.KeplerIapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import javax.validation.Valid;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode")
@JsonSubTypes({@JsonSubTypes.Type(value = ItunesVerificationRequest.class, name = "ITUNES"), @JsonSubTypes.Type(value = AmazonIapVerificationRequest.class, name = "AMAZON_IAP"), @JsonSubTypes.Type(value = GooglePlayVerificationRequest.class, name = "GOOGLE_IAP"), @JsonSubTypes.Type(value = KeplerIapVerificationRequest.class, name = "KEPLER_IAP")})
public abstract class IapVerificationRequestV2 {

    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;

    @Valid
    @Analysed
    private PageUrlDetails pageDetails;


    @Analysed
    private ProductDetailsDto productDetails;

    @Analysed
    private SessionDetails sessionDetails;

    @Analysed
    private GeoLocation geoLocation;

    private String paymentCode;

    private boolean originalSid;

    public PaymentGateway getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }
    public void setOriginalSid() {
        this.originalSid = StringUtils.isNotBlank(this.sessionDetails.getSessionId());
    }

    public PurchaseDetails getPurchaseDetails() {
        return PurchaseDetails.builder()
                .appDetails(getAppDetails())
                .userDetails(getUserDetails())
                .productDetails(getProductDetails())
                .pageUrlDetails(getPageDetails())
                .sessionDetails(getSessionDetails())
                .geoLocation(getGeoLocation())
                .build();
    }
}
