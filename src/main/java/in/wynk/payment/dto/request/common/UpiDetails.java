package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AnalysedEntity
public class UpiDetails implements Serializable {
    @Analysed
    private String vpa;
    @Analysed
    private boolean intent;
}
