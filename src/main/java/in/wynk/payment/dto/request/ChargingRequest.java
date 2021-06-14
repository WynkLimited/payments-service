package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.phonepe.autodebit.PhonePeAutoDebitChargeRequest;
import lombok.*;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode",defaultImpl = ChargingRequest.class,visible=true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PhonePeAutoDebitChargeRequest.class, name = "PHONEPE_AUTO_DEBIT")


})
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@SuperBuilder
@NoArgsConstructor
@AnalysedEntity
public class ChargingRequest {
    @Analysed
    private int planId;
    @Analysed
    private String itemId;
    @Analysed
    private boolean autoRenew;
    @Analysed
    private String couponId;
    @Analysed
    private PaymentCode paymentCode;
    @Analysed(name = "paymentMode")
    private String paymentMode;
    @Analysed(name = "bankName")
    private String merchantName;

}
