package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
@AnalysedEntity
public class UpiDetails implements Serializable {

    private String vpa;

    @Analysed
    private boolean intent;
}
