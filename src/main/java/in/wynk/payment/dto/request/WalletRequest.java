package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.paytm.PaytmWalletAddMoneyRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletLinkRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletValidateLinkRequest;
import lombok.Getter;

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
public class WalletRequest {

    private PaymentCode paymentCode;

}
