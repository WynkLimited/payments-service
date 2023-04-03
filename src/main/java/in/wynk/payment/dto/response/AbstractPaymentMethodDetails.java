package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentMethodDetails extends AbstractPaymentGroupsDTO {
    private String code;
    private String health;
}
