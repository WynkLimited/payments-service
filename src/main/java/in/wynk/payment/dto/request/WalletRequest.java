package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.constant.PaymentOption;
import lombok.Getter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        property = "@class"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaytmWalletLinkRequest.class, name = "PaytmWalletLinkRequest"),
        @JsonSubTypes.Type(value = PaytmWalletValidateLinkRequest.class, name = "PaytmWalletValidateLinkRequest")
})
@Getter
public class WalletRequest {

    private PaymentOption paymentOption;

}
