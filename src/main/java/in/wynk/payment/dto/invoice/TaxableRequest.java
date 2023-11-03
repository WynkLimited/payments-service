package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class TaxableRequest {
    @Analysed
    private final String supplierStateCode;
    @Analysed
    private final String consumerStateCode;
    @Analysed
    private final String supplierStateName;
    @Analysed
    private final String consumerStateName;
    @Analysed
    private final double amount;
    @Analysed
    private final double gstPercentage;

    @Override
    public String toString () {
        return "TaxableRequest{" +
                "supplierStateCode='" + supplierStateCode + '\'' +
                ", consumerStateCode='" + consumerStateCode + '\'' +
                ", supplierStateName='" + supplierStateName + '\'' +
                ", consumerStateName='" + consumerStateName + '\'' +
                ", amount=" + amount +
                ", gstPercentage=" + gstPercentage +
                '}';
    }
}
