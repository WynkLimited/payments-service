package in.wynk.payment.core.constant;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;

@AnalysedEntity
@AllArgsConstructor
public enum PaymentGroup {

    CARD("CARD"),
    WALLET("WALLET"),
    NET_BANKING("NET_BANKING"),
    UPI("UPI");
    @Analysed
    private final String value;
}
