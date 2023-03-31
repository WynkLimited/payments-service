package in.wynk.payment.dto.aps.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Nishesh Pandey
 */

@Getter
@RequiredArgsConstructor
@AnalysedEntity
public enum DeleteType {
    VPA("vpa"),
    CARD("card");
    @Analysed
    private final String type;
}
