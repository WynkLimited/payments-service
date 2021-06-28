package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.phonepe.autodebit.PhonePeAutoDebitTopUpRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "paymentCode", visible = true, defaultImpl = WalletTopUpRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PhonePeAutoDebitTopUpRequest.class, name = "PHONEPE_AUTO_DEBIT")
})
public class WalletTopUpRequest<T extends IPurchaseDetails> {
    @Analysed
    private PaymentCode paymentCode;
    @Analysed
    private T purchaseDetails;
}