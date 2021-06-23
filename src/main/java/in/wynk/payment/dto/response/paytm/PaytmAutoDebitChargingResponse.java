package in.wynk.payment.dto.response.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaytmAutoDebitChargingResponse extends AbstractChargingResponse {

    @Analysed
    private String info;

    @Analysed
    private String redirectUrl;

    @Analysed
    private boolean deficit;

}