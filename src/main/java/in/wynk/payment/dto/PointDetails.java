package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.*;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class PointDetails extends AbstractProductDetails {
    @Analysed
    private String itemId;

    @Override
    public String getId() {
        return itemId;
    }

    @Override
    public String getType() {
        return BaseConstants.POINT;
    }
}
