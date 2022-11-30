package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@AnalysedEntity
public class SavedCardDetails extends AbstractCardDetails {
    @Analysed
    private String cardToken;
    @Analysed
    private String cvv;

    @Override
    @Analysed(name = "cardType")
    public String getType() {
        return "SAVED";
    }

}
