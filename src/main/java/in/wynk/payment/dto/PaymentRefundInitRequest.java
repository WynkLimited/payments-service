package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundInitRequest extends AbstractPaymentRefundRequest {
    @Analysed
    private Double amount;
    @NotBlank
    @Analysed
    private String reason;

    @NotBlank
    @Analysed
    private String originalTransactionId;

}