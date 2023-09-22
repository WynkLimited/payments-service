package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@AnalysedEntity
public class TaxableResponse {
    @Analysed
    private final double taxableAmount;
    @Analysed
    private final double taxAmount;
    @Analysed
    private final List<TaxDetailsDTO> taxDetails;

    @Override
    public String toString () {
        final StringBuilder taxDetailsList = new StringBuilder();
        for(TaxDetailsDTO dto : taxDetails){
            taxDetailsList.append(dto.toString());
            taxDetailsList.append(",");
        }
        return "TaxableResponse{" +
                "taxableAmount=" + taxableAmount +
                ", taxAmount=" + taxAmount +
                ", taxDetails=[" + taxDetailsList +
                "]}";
    }
}
