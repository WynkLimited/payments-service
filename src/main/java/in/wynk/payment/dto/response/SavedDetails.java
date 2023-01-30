package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import net.bytebuddy.implementation.bind.annotation.Super;


/**
 * @author Nishesh Pandey
 */
@Getter
@AllArgsConstructor
@SuperBuilder
@AnalysedEntity
public class SavedDetails {
    private String id;
}
