package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AnalysedEntity
public class CustomerWindBackRequest {
    @Analysed
    private String dropoutTransactionId;
    @Analysed
    private Map<String, Object> params;

}
