package in.wynk.payment.dto.request.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@AnalysedEntity
public class UpiDetails {
    @Analysed
    private String vpa;

    @Analysed
    @JsonProperty("isIntent")
    private boolean intent;
}
