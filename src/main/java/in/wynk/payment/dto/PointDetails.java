package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PointDetails extends AbstractProductDetails {
    @Analysed
    private String itemId;

    @Override
    @Analysed
    public boolean isAutoRenew() {
        return false;
    }
}
