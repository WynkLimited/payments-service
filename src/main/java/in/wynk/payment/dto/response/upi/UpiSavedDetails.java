package in.wynk.payment.dto.response.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.SavedDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpiSavedDetails extends SavedDetails {
    private String vpa;
}
