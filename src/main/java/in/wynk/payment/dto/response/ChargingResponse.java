package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingResponse {

    @Analysed
    private String info;

    @Analysed
    private String redirectUrl;

    @Analysed
    private boolean deficit;

}