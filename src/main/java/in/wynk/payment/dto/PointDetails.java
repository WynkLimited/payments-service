package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Objects;

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
    public String getId () {
        return itemId;
    }


    public String getTitle () {
        if (Objects.isNull(title)) {
            SessionDTO sessionDto = SessionContextHolder.getBody();
            return sessionDto.get("title");
        }
        return title;
    }

    public String getPrice () {
        if (Objects.isNull(price)) {
            SessionDTO sessionDto = SessionContextHolder.getBody();
            return sessionDto.get("price");
        }
        return price;
    }

    public String getSkuId () {
        if (Objects.isNull(skuId)) {
            SessionDTO sessionDto = SessionContextHolder.getBody();
            return sessionDto.get("skuId");
        }
        return skuId;
    }

    public void setTitle (String title) {
        if (Objects.isNull(title)) {
            SessionDTO sessionDto = SessionContextHolder.getBody();
            this.title = sessionDto.get("title");
        } else {
            this.title = title;
        }
    }

    @Override
    public String getType () {
        return BaseConstants.POINT;
    }

}