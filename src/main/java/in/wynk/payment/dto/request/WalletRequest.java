package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WalletLinkRequest.class, name = "PaytmWalletLink"),
        @JsonSubTypes.Type(value = WalletValidateLinkRequest.class, name = "PaytmWalletValidateLink"),
        @JsonSubTypes.Type(value = WalletAddMoneyRequest.class, name = "PaytmWalletAddMoney")
})
@Getter
@Setter
@Deprecated
public class WalletRequest {

    private PaymentCode paymentCode;

}
