package in.wynk.payment.dto.request.common;


import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@AnalysedEntity
public class CardExpiryInfo {
    @Analysed
    private String month;
    @Analysed
    private String year;
}
