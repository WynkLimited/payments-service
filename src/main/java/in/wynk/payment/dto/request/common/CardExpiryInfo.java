package in.wynk.payment.dto.request.common;


import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AnalysedEntity
public class CardExpiryInfo implements Serializable {
    @Analysed
    private String month;
    @Analysed
    private String year;
}
