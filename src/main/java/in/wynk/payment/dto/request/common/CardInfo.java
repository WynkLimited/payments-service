package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@AnalysedEntity
public class CardInfo {
    @Analysed
    private String type;
    @Analysed
    private String category;
    @Analysed
    private String issuedBy;
    @Analysed
    private String bankCode;
    @Analysed
    private String cvv;
}
