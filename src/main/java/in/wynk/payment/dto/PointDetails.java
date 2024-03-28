package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.CacheBeanNameConstants.ITEM_DTO;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PointDetails extends AbstractProductDetails {

    @NotNull
    @Analysed
    private String itemId;
    @NotNull
    @Analysed
    private String title;
    @NotNull
    @Analysed
    private String price;
    @NotNull
    @Analysed
    private String skuId;

    @Override
    public String getId() {
        return itemId;
    }

    @Override
    public String getType() {
        return BaseConstants.POINT;
    }

}