package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.InvoiceTaxType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
public class TaxDetailsDTO {
    @Analysed
    private InvoiceTaxType taxType;
    @Analysed
    private double rate;
    @Analysed
    private double amount;

    @Override
    public String toString () {
        return "TaxDetailsDTO{" +
                "taxType=" + taxType +
                ", rate=" + rate +
                ", amount=" + amount +
                '}';
    }
}
