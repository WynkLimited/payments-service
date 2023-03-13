package in.wynk.payment.dto.request.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpiDetails implements Serializable {
    @Analysed
    private String vpa;

    @Analysed
    @JsonProperty("isIntent")
    private boolean intent;
}
