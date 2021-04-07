package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.paytm.WalletAddMoneyRequest;
import in.wynk.payment.dto.paytm.WalletLinkRequest;
import in.wynk.payment.dto.paytm.WalletValidateLinkRequest;
import in.wynk.payment.dto.phonepe.PhonePeAutoDebitOtpRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WalletLinkRequest.class, name = "PaytmWalletLink"),
        @JsonSubTypes.Type(value = WalletValidateLinkRequest.class, name = "PaytmWalletValidateLink"),
        @JsonSubTypes.Type(value = WalletAddMoneyRequest.class, name = "PaytmWalletAddMoney"),
        @JsonSubTypes.Type(value = PhonePeAutoDebitOtpRequest.class, name = "PhonePeWalletLinkRequest")
})
@Getter
@Setter
@NoArgsConstructor
public abstract class WalletRequest {
    private PaymentCode paymentCode;

}
