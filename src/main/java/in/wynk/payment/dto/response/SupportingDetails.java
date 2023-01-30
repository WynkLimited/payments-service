package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@AllArgsConstructor
@SuperBuilder
@AnalysedEntity
public class SupportingDetails {
    private boolean autoRenewSupported;

}
