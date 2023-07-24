package in.wynk.payment.dto.request.charge;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.CacheBeanNameConstants;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.PaymentDetails;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.dto.request.charge.netbanking.NetBankingPaymentDetails;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.charge.wallet.WalletPaymentDetails;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.annotation.PersistenceConstructor;

import javax.validation.constraints.NotNull;

import static in.wynk.payment.constant.CardConstants.CARD;
import static in.wynk.payment.constant.NetBankingConstants.NET_BANKING;
import static in.wynk.payment.constant.UpiConstants.UPI;
import static in.wynk.payment.constant.WalletConstants.WALLET;

@Getter
@Setter
@SuperBuilder
@AnalysedEntity
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentMode", visible = true, defaultImpl = PaymentDetails.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UpiPaymentDetails.class, name =  UPI),
        @JsonSubTypes.Type(value = CardPaymentDetails.class, name = CARD),
        @JsonSubTypes.Type(value = WalletPaymentDetails.class, name = WALLET),
        @JsonSubTypes.Type(value = NetBankingPaymentDetails.class, name =  NET_BANKING)
})
public abstract class AbstractPaymentDetails implements IPaymentDetails {

    @Analysed
    @MongoBaseEntityConstraint(beanName = CacheBeanNameConstants.COUPON)
    private String couponId;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = CacheBeanNameConstants.PAYMENT_METHOD)
    private String paymentId;

    @Analysed(name = "paymentMode")
    private String paymentMode;

    @Analysed(name = "bankName")
    private String merchantName;

    @Analysed
    private boolean autoRenew;

    @Analysed
    private boolean trialOpted;

    @Analysed
    private boolean pennyDrop;

    public AbstractPaymentDetails(String couponId, @NotNull String paymentId, String paymentMode, String merchantName, boolean autoRenew, boolean trialOpted,boolean pennyDrop) {
        this.couponId = couponId;
        this.paymentId = paymentId;
        this.paymentMode = paymentMode;
        this.merchantName = merchantName;
        this.autoRenew = autoRenew;
        this.trialOpted = trialOpted;
        this.pennyDrop = pennyDrop;
    }

    @PersistenceConstructor
    public AbstractPaymentDetails() {
    }

    public boolean isTrialOpted() {
        return BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<PaymentMethod, String>>() {
        }).get(paymentId).isTrialSupported() && trialOpted;
    }

    @Override
    public boolean isPennyDrop () {
        return this.pennyDrop;
    }
}
