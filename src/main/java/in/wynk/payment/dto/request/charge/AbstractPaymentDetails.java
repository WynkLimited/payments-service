package in.wynk.payment.dto.request.charge;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.dto.PaymentDetails;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.netbanking.NetBankingPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.charge.wallet.WalletPaymentDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Getter
@SuperBuilder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", defaultImpl = PaymentDetails.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiPaymentDetails.class, name =  PaymentConstants.UPI),
        @JsonSubTypes.Type(value = CardPaymentDetails.class, name = PaymentConstants.CARD),
        @JsonSubTypes.Type(value = WalletPaymentDetails.class, name = PaymentConstants.WALLET),
        @JsonSubTypes.Type(value = NetBankingPaymentDetails.class, name =  PaymentConstants.NET_BANKING)
})
public abstract class AbstractPaymentDetails implements IPaymentDetails {

    @Analysed
    private String couponId;

    @NotNull
    @Analysed
    private String paymentId;

    @Analysed(name = "paymentMode")
    private String paymentMode;

    @Analysed(name = "bankName")
    private String merchantName;

    @Analysed
    private boolean autoRenew;

    @Analysed
    private boolean trialOpted;

    public abstract String getPaymentGroup();

}
