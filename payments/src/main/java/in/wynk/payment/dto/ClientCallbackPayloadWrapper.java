package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class ClientCallbackPayloadWrapper<T> {

    @Analysed
    private String clientAlias;
    @Analysed
    private T payload;

}
