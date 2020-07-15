package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
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
public class IapVerificationRequest {

    private String uid;
    private int planId;
    private String deviceId;
    private String msisdn;
    private PaymentCode paymentCode;

}
