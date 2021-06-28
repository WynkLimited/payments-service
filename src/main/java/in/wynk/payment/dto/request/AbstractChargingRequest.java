package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.response.phonepe.auto.PhonePeChargingRequest;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode", visible = true, defaultImpl = DefaultChargingRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PhonePeChargingRequest.class, name = "PHONEPE_AUTO_DEBIT")
})
public abstract class AbstractChargingRequest<T extends IPurchaseDetails> {
    @Analysed
    private PaymentCode paymentCode;
    @Analysed
    private T purchaseDetails;
}
