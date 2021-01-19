package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.itune.ItunesVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ItunesVerificationRequest.class, name = "ITUNES"),
        @JsonSubTypes.Type(value = AmazonIapVerificationRequest.class, name = "AMAZON_IAP")
})
@Getter
@AnalysedEntity
@SuperBuilder
@NoArgsConstructor
public abstract class IapVerificationRequest {

    @Analysed
    private String os;
    @Analysed
    private String uid;
    @Setter
    @Analysed
    private String sid;
    @Analysed
    private int planId;
    @Analysed
    private int buildNo;
    @Analysed
    private String msisdn;
    @Analysed
    private String service;
    @Analysed
    private String deviceId;

    private PaymentCode paymentCode;

}
