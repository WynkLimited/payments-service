package in.wynk.payment.dto.request.common;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;

@Getter
@AnalysedEntity
public class FreshCardDetails extends AbstractCardDetails {
    @Analysed
    private String cardHolderName;
    @Analysed
    private String cardNumber;
    @Analysed
    private String cvv;
    @Analysed
    private CardExpiryInfo expiryInfo;

    @Override
    public String getType() {
        return "FRESH";
    }
}
