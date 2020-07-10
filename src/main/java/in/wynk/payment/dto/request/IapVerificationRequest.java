package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletAddMoneyRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletLinkRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletValidateLinkRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ItunesVerificationRequest.class, name = "ITUNES"),
        @JsonSubTypes.Type(value = AmazonIapVerificationRequest.class, name = "AMAZON_IAP")
})

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IapVerificationRequest {

    private String uid;
    private int planId;
    private PaymentCode paymentCode;

}
