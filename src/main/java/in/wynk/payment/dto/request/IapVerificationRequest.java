package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.itune.ItunesVerificationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static in.wynk.payment.core.constant.BeanConstant.AMAZON_IAP_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.BeanConstant.ITUNES_PAYMENT_SERVICE;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ItunesVerificationRequest.class, name = ITUNES_PAYMENT_SERVICE),
        @JsonSubTypes.Type(value = AmazonIapVerificationRequest.class, name = AMAZON_IAP_PAYMENT_SERVICE)
})

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@AnalysedEntity
public class IapVerificationRequest {
    @Analysed
    private String uid;
    @Analysed
    private int planId;
    @Analysed
    private String deviceId;
    @Analysed
    private String msisdn;
    @Analysed
    private String service;
    @Analysed
    private String sid;
    private PaymentCode paymentCode;

}
