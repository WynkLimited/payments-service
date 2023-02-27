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
    @JsonProperty("isIntent")
    @Analysed
    private boolean intent;
    @JsonProperty("isSeamless")
    @Analysed
    private boolean seamless;
}
