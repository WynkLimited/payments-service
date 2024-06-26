package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.dto.ProductDetailsDto;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class GooglePlayProductDetails extends ProductDetailsDto {
    @NotEmpty
    @Analysed
    private String skuId;
    @Analysed
    private String title;

    public String getTitle () {
        if (Objects.isNull(title)) {
            SessionDTO sessionDto = SessionContextHolder.getBody();
            return sessionDto.get("title");
        }
        return title;
    }
    public void setTitle (String title) {
        if (Objects.isNull(title)) {
            SessionDTO sessionDto = SessionContextHolder.getBody();
            this.title = sessionDto.get("title");
        } else {
            this.title = title;
        }
    }
}
