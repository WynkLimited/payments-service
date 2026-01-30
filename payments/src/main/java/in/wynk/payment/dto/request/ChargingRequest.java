package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.dto.payu.PayUChargingRequest;
import in.wynk.payment.dto.phonepe.autodebit.PhonePeAutoDebitChargeRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.PAYMENT_CODE;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = PAYMENT_CODE, defaultImpl = ChargingRequest.class, visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = PhonePeAutoDebitChargeRequest.class, name = "PHONEPE_AUTO_DEBIT"), @JsonSubTypes.Type(value = PayUChargingRequest.class, name = "PAYU")})
public class ChargingRequest {

    @Analysed
    private int planId;

    @Analysed
    private boolean autoRenew;

    @Analysed
    private String itemId;

    @Analysed
    private String couponId;

    @Analysed(name = "paymentMode")
    private String paymentMode;

    @Analysed(name = "bankName")
    private String merchantName;

    @Analysed
    private PaymentGateway paymentGateway;

}