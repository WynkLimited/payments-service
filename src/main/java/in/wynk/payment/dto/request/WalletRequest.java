package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.paytm.PaytmWalletAddMoneyRequest;
import in.wynk.payment.dto.paytm.PaytmWalletLinkRequest;
import in.wynk.payment.dto.paytm.PaytmWalletValidateLinkRequest;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaytmWalletLinkRequest.class, name = "PaytmWalletLink"),
        @JsonSubTypes.Type(value = PaytmWalletValidateLinkRequest.class, name = "PaytmWalletValidateLink"),
        @JsonSubTypes.Type(value = PaytmWalletAddMoneyRequest.class, name = "PaytmWalletAddMoney")
})
@Getter
@Setter
public class WalletRequest {

    private PaymentCode paymentCode;

}
