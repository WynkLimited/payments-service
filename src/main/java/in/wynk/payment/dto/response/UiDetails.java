package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@AllArgsConstructor
@Builder
@AnalysedEntity
public class UiDetails {
    private String icon;
}
